package com.tapbot.core.runner

import com.tapbot.core.model.BotCredentialSpec
import com.tapbot.core.model.BotInstanceStatus
import com.tapbot.core.model.BotMetadata
import com.tapbot.core.model.BotPackageInfo
import com.tapbot.core.model.BotUpdateProgress
import com.tapbot.core.model.BotVersion
import com.tapbot.core.model.TelegramUser
import com.tapbot.core.model.UpdateState
import com.tapbot.core.network.BotPackageDownloader
import com.tapbot.core.network.ManifestValidator
import com.tapbot.core.network.TelegramApiClient
import com.tapbot.core.runner.manager.DefaultBotInstanceManager
import com.tapbot.core.security.CredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BotUpdateRollbackTest {

    private lateinit var tempDir: File
    private lateinit var storageFile: File
    private lateinit var installDir: File
    private lateinit var credStore: FakeCredentialStore
    private lateinit var api: FakeTelegramApiClient

    private class FakeCredentialStore(var storedToken: String? = null) : CredentialStore {
        val map = mutableMapOf<String, MutableMap<String, String>>()

        override suspend fun saveCredential(botId: String, key: String, secretValue: String) {
            map.getOrPut(botId) { mutableMapOf() }[key] = secretValue
            if (key == "telegram_bot_token" || key == "bot_token") storedToken = secretValue
        }
        override suspend fun getCredential(botId: String, key: String): String? =
            map[botId]?.get(key) ?: if (key == "telegram_bot_token" || key == "bot_token") storedToken else null

        override suspend fun deleteCredential(botId: String, key: String) {
            map[botId]?.remove(key)
        }
        override suspend fun hasCredential(botId: String, key: String): Boolean =
            map[botId]?.containsKey(key) == true || (key == "telegram_bot_token" && !storedToken.isNullOrBlank())

        override suspend fun getAllCredentials(botId: String): Map<String, String> =
            map[botId]?.toMap() ?: if (storedToken != null) mapOf("bot_token" to storedToken!!) else emptyMap()

        override suspend fun deleteCredentials(botId: String) {
            map.remove(botId)
            storedToken = null
        }
        override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean =
            requiredKeys.all { hasCredential(botId, it) }

        override suspend fun saveToken(token: String) { storedToken = token }
        override suspend fun getToken(): String? = storedToken
        override suspend fun clearToken() { storedToken = null }
        override suspend fun hasToken(): Boolean = !storedToken.isNullOrBlank()
    }

    private class FakeTelegramApiClient : TelegramApiClient() {
        override suspend fun getMe(token: String): Result<TelegramUser> {
            return if (token.startsWith("VALID")) {
                Result.success(TelegramUser(id = 1, isBot = true, firstName = "Bot", username = "valid_bot"))
            } else {
                Result.failure(IllegalArgumentException("Invalid token"))
            }
        }
    }

    private class MockPackageDownloader(val sourceFile: File) : BotPackageDownloader {
        override suspend fun downloadPackage(
            packageUrl: String,
            expectedSha256: String,
            targetFile: File,
            onProgress: (Float) -> Unit
        ): Result<File> {
            onProgress(0.5f)
            sourceFile.copyTo(targetFile, overwrite = true)
            onProgress(1.0f)
            return Result.success(targetFile)
        }
    }

    @Before
    fun setUp() {
        tempDir = File.createTempFile("rollback_test_", "").apply { delete(); mkdirs() }
        storageFile = File(tempDir, "instances.json")
        installDir = File(tempDir, "installed_packages").apply { mkdirs() }
        credStore = FakeCredentialStore("VALID_TOKEN_123")
        api = FakeTelegramApiClient()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createZipPackage(file: File, botId: String, version: String): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            val manifestJson = """
                {
                    "id": "$botId",
                    "name": "Test Bot",
                    "version": "$version",
                    "runtime": "native_art",
                    "minimumAppVersion": 1
                }
            """.trimIndent()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("DEX_CODE_STUB_DATA".toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private fun sampleBot(id: String, name: String, version: String = "1.0.0") = BotMetadata(
        id = id,
        name = name,
        summary = "Summary for $name",
        description = "Description for $name",
        author = "TapBot Team",
        version = version,
        iconUrl = "https://cdn.tapbot.dev/$id.png",
        category = "Utilities",
        requiredCredentials = listOf(BotCredentialSpec.TELEGRAM_BOT_TOKEN),
        packageInfo = BotPackageInfo(
            packageUrl = "https://cdn.tapbot.dev/$id.botpkg",
            sha256Checksum = "dummy",
            runtimeType = "native_art"
        )
    )

    @Test
    fun `successful update from v1_0_0 to v1_4_0 preserves keystore token and cleans up backup`() = runBlocking {
        var managerInstance: DefaultBotInstanceManager? = null
        val manager = DefaultBotInstanceManager(
            storageFile = storageFile,
            installDir = installDir,
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, instId ->
                if (action == BotForegroundService.ACTION_START_BOT && instId != null) {
                    managerInstance?.updateInstanceStatus(instId, BotInstanceStatus.Running())
                }
            }
        )
        managerInstance = manager

        // 1. Initial installation v1.0.0
        val pkgV1 = File(tempDir, "v1.botpkg")
        createZipPackage(pkgV1, "bot_music", "1.0.0")
        val instResult = manager.install(sampleBot("bot_music", "Music Bot", "1.0.0"), pkgV1)
        assertTrue(instResult.isSuccess)
        val instance = instResult.getOrThrow()

        // Configure credential and start
        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN_SECRET")
        val startResult = manager.start(instance.installationId)
        assertTrue(startResult.isSuccess)
        assertTrue(manager.getInstance(instance.installationId)!!.status.isRunning)

        // 2. Prepare target version v1.4.0
        val pkgV14 = File(tempDir, "v14.botpkg")
        createZipPackage(pkgV14, "bot_music", "1.4.0")
        val v14Sha = sha256Of(pkgV14)
        val targetVersion = BotVersion(
            id = "ver_v14",
            botId = "bot_music",
            version = "1.4.0",
            packageUrl = "https://cdn.tapbot.dev/bot_music_v1.4.0.botpkg",
            sha256 = v14Sha,
            packageSize = pkgV14.length(),
            releaseNotes = "Major performance improvements and new commands",
            isPublished = true
        )

        val progressList = mutableListOf<BotUpdateProgress>()

        // 3. Perform update with rollback engine
        val updateRes = manager.updateWithRollback(
            installationId = instance.installationId,
            targetVersion = targetVersion,
            packageDownloader = MockPackageDownloader(pkgV14),
            manifestValidator = ManifestValidator(),
            onProgress = { progressList.add(it) }
        )

        assertTrue("Update must succeed: ${updateRes.exceptionOrNull()?.message}", updateRes.isSuccess)
        val updatedInstance = updateRes.getOrThrow()

        // 4. Verification of state transitions
        val states = progressList.map { it.state }
        assertTrue(states.contains(UpdateState.CHECKING))
        assertTrue(states.contains(UpdateState.DOWNLOADING))
        assertTrue(states.contains(UpdateState.VERIFYING))
        assertTrue(states.contains(UpdateState.INSTALLING))
        assertTrue(states.contains(UpdateState.STARTING))
        assertTrue(states.contains(UpdateState.SUCCESS))
        assertFalse(states.contains(UpdateState.ROLLING_BACK))

        // 5. Verification of updated version and running status
        assertEquals("1.4.0", updatedInstance.version)
        assertEquals("1.4.0", manager.getInstance(instance.installationId)!!.version)
        assertTrue(manager.getInstance(instance.installationId)!!.status.isRunning)

        // 6. Verification of Keystore token preservation (Zero re-entry required!)
        assertEquals("VALID_TOKEN_SECRET", credStore.getCredential("bot_music", "bot_token"))

        // 7. Verification that staged backup was safely removed after startup health check passed
        val backupFiles = installDir.listFiles { _, name -> name.contains(".backup_") } ?: emptyArray()
        assertEquals(0, backupFiles.size)
    }

    @Test
    fun `startup failure on new version triggers atomic rollback restoring v1_0_0 and running state`() = runBlocking {
        var simulateCrashOnV14 = true
        var managerInstance: DefaultBotInstanceManager? = null

        val manager = DefaultBotInstanceManager(
            storageFile = storageFile,
            installDir = installDir,
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, instId ->
                val current = managerInstance?.getInstance(instId ?: "")
                if (simulateCrashOnV14 && current?.version == "1.4.0") {
                    // Simulate runtime startup crash on new version!
                    throw IllegalStateException("ART runtime crashed on initializing v1.4.0 dex entrypoint")
                }
                if (action == BotForegroundService.ACTION_START_BOT && instId != null) {
                    managerInstance?.updateInstanceStatus(instId, BotInstanceStatus.Running())
                }
            }
        )
        managerInstance = manager

        // 1. Initial installation v1.0.0
        val pkgV1 = File(tempDir, "v1.botpkg")
        createZipPackage(pkgV1, "bot_music", "1.0.0")
        val instResult = manager.install(sampleBot("bot_music", "Music Bot", "1.0.0"), pkgV1)
        val instance = instResult.getOrThrow()

        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN_SECRET")
        manager.start(instance.installationId)
        assertTrue(manager.getInstance(instance.installationId)!!.status.isRunning)

        // 2. Prepare target version v1.4.0
        val pkgV14 = File(tempDir, "v14.botpkg")
        createZipPackage(pkgV14, "bot_music", "1.4.0")
        val v14Sha = sha256Of(pkgV14)
        val targetVersion = BotVersion(
            id = "ver_v14",
            botId = "bot_music",
            version = "1.4.0",
            packageUrl = "https://cdn.tapbot.dev/bot_music_v1.4.0.botpkg",
            sha256 = v14Sha,
            isPublished = true
        )

        val progressList = mutableListOf<BotUpdateProgress>()

        // 3. Perform update - which will fail on startup and trigger atomic rollback!
        val updateRes = manager.updateWithRollback(
            installationId = instance.installationId,
            targetVersion = targetVersion,
            packageDownloader = MockPackageDownloader(pkgV14),
            manifestValidator = ManifestValidator(),
            onProgress = { progressList.add(it) }
        )

        // 4. Verify update returned failure
        assertTrue(updateRes.isFailure)

        // 5. Verify states traversed included ROLLING_BACK and FAILED
        val states = progressList.map { it.state }
        assertTrue("Must have triggered ROLLING_BACK state", states.contains(UpdateState.ROLLING_BACK))
        assertTrue("Must end in FAILED state", states.contains(UpdateState.FAILED))

        // 6. Verify version was ATOMICALLY RESTORED to v1.0.0
        val restoredInstance = manager.getInstance(instance.installationId)
        assertNotNull(restoredInstance)
        assertEquals("1.0.0", restoredInstance!!.version)

        // 7. Verify previous version was restarted and is Running
        assertTrue("Restored v1.0.0 bot must be running", restoredInstance.status.isRunning)

        // 8. Verify credentials were NEVER lost during rollback
        assertEquals("VALID_TOKEN_SECRET", credStore.getCredential("bot_music", "bot_token"))

        // 9. Verify failed v1.4.0 package was deleted
        val failedPkgFile = File(installDir, "bot_music_1.4.0.botpkg")
        assertFalse("Failed new package must be deleted after rollback", failedPkgFile.exists())
    }

    @Test
    fun `corrupt package checksum rejects update at VERIFYING stage without stopping running bot`() = runBlocking {
        val actions = mutableListOf<String>()
        var managerInstance: DefaultBotInstanceManager? = null
        val manager = DefaultBotInstanceManager(
            storageFile = storageFile,
            installDir = installDir,
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { action, instId ->
                actions.add(action)
                if (action == BotForegroundService.ACTION_START_BOT && instId != null) {
                    managerInstance?.updateInstanceStatus(instId, BotInstanceStatus.Running())
                }
            }
        )
        managerInstance = manager

        // 1. Initial installation v1.0.0
        val pkgV1 = File(tempDir, "v1.botpkg")
        createZipPackage(pkgV1, "bot_music", "1.0.0")
        val instance = manager.install(sampleBot("bot_music", "Music Bot", "1.0.0"), pkgV1).getOrThrow()

        credStore.saveCredential("bot_music", "bot_token", "VALID_TOKEN_SECRET")
        manager.start(instance.installationId)
        actions.clear()

        // 2. Corrupt/mismatched checksum
        val pkgV14 = File(tempDir, "v14.botpkg")
        createZipPackage(pkgV14, "bot_music", "1.4.0")
        val corruptedChecksum = "0000000000000000000000000000000000000000000000000000000000000000"
        val targetVersion = BotVersion(
            id = "ver_v14",
            botId = "bot_music",
            version = "1.4.0",
            packageUrl = "https://cdn.tapbot.dev/pkg.botpkg",
            sha256 = corruptedChecksum,
            isPublished = true
        )

        val progressList = mutableListOf<BotUpdateProgress>()

        // 3. Run update
        val res = manager.updateWithRollback(
            installationId = instance.installationId,
            targetVersion = targetVersion,
            packageDownloader = MockPackageDownloader(pkgV14),
            onProgress = { progressList.add(it) }
        )

        // 4. Must fail at VERIFYING stage
        assertTrue(res.isFailure)
        val states = progressList.map { it.state }
        assertTrue(states.contains(UpdateState.VERIFYING))
        assertTrue(states.contains(UpdateState.FAILED))
        assertFalse(states.contains(UpdateState.INSTALLING))
        assertFalse(states.contains(UpdateState.STARTING))

        // 5. Old bot was NEVER stopped!
        assertFalse(actions.contains(BotForegroundService.ACTION_STOP_BOT))
        assertTrue(manager.getInstance(instance.installationId)!!.status.isRunning)
        assertEquals("1.0.0", manager.getInstance(instance.installationId)!!.version)
    }

    @Test
    fun `downgrade attack attempt is rejected at CHECKING stage`() = runBlocking {
        val manager = DefaultBotInstanceManager(
            storageFile = storageFile,
            installDir = installDir,
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { _, _ -> }
        )

        // 1. Initial installation v1.4.0
        val pkgV14 = File(tempDir, "v14.botpkg")
        createZipPackage(pkgV14, "bot_music", "1.4.0")
        val instance = manager.install(sampleBot("bot_music", "Music Bot", "1.4.0"), pkgV14).getOrThrow()

        // 2. Candidate version is an older version v1.2.0 (Downgrade Attack)
        val pkgV12 = File(tempDir, "v12.botpkg")
        createZipPackage(pkgV12, "bot_music", "1.2.0")
        val targetVersion = BotVersion(
            id = "ver_v12",
            botId = "bot_music",
            version = "1.2.0",
            packageUrl = "https://cdn.tapbot.dev/pkg.botpkg",
            sha256 = sha256Of(pkgV12),
            isPublished = true
        )

        val progressList = mutableListOf<BotUpdateProgress>()
        val res = manager.updateWithRollback(
            installationId = instance.installationId,
            targetVersion = targetVersion,
            packageDownloader = MockPackageDownloader(pkgV12),
            onProgress = { progressList.add(it) }
        )

        // 3. Fails immediately at CHECKING stage
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is SecurityException)
        val states = progressList.map { it.state }
        assertEquals(listOf(UpdateState.CHECKING, UpdateState.FAILED), states)
        assertEquals("1.4.0", manager.getInstance(instance.installationId)!!.version)
    }

    @Test
    fun `manifest version mismatch is rejected before installation`() = runBlocking {
        val manager = DefaultBotInstanceManager(
            storageFile = storageFile,
            installDir = installDir,
            credentialStore = credStore,
            telegramApi = api,
            serviceLauncher = { _, _ -> }
        )

        val pkgV1 = File(tempDir, "v1.botpkg")
        createZipPackage(pkgV1, "bot_music", "1.0.0")
        val instance = manager.install(sampleBot("bot_music", "Music Bot", "1.0.0"), pkgV1).getOrThrow()

        // Package with mismatched manifest version (manifest says 1.3.0, but target says 1.4.0)
        val mismatchedPkg = File(tempDir, "mismatched.botpkg")
        createZipPackage(mismatchedPkg, "bot_music", "1.3.0")
        val targetVersion = BotVersion(
            id = "ver_v14",
            botId = "bot_music",
            version = "1.4.0",
            packageUrl = "https://cdn.tapbot.dev/pkg.botpkg",
            sha256 = sha256Of(mismatchedPkg),
            isPublished = true
        )

        val res = manager.updateWithRollback(
            installationId = instance.installationId,
            targetVersion = targetVersion,
            packageDownloader = MockPackageDownloader(mismatchedPkg),
            manifestValidator = ManifestValidator()
        )

        assertTrue(res.isFailure)
        assertEquals("1.0.0", manager.getInstance(instance.installationId)!!.version)
    }
}
