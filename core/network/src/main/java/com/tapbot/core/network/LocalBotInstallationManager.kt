package com.tapbot.core.network

import com.tapbot.core.model.BotMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class InstalledBotRecord(
    val botId: String,
    val name: String,
    val version: String,
    val packagePath: String,
    val runtimeType: String,
    val installedAt: Long = System.currentTimeMillis()
)

/**
 * Interface managing local bot package installations on the Android device.
 */
interface LocalBotInstallationManager {
    fun isInstalled(botId: String): Boolean
    fun getInstalledVersion(botId: String): String?
    fun installBot(bot: BotMetadata, packageFile: File): Result<Unit>
    fun uninstallBot(botId: String): Result<Unit>
    fun getInstalledBots(): List<InstalledBotRecord>
}

/**
 * Default persistent implementation storing installation state in private JSON storage.
 */
class DefaultLocalBotInstallationManager(
    private val storageFile: File? = null,
    private val installDir: File? = null
) : LocalBotInstallationManager {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val installedMap = mutableMapOf<String, InstalledBotRecord>()

    init {
        loadFromDisk()
    }

    @Synchronized
    override fun isInstalled(botId: String): Boolean {
        return installedMap.containsKey(botId)
    }

    @Synchronized
    override fun getInstalledVersion(botId: String): String? {
        return installedMap[botId]?.version
    }

    @Synchronized
    override fun installBot(bot: BotMetadata, packageFile: File): Result<Unit> = runCatching {
        val targetArchive: File
        if (installDir != null) {
            installDir.mkdirs()
            targetArchive = File(installDir, "${bot.id}_${bot.version}.botpkg")
            if (packageFile.absolutePath != targetArchive.absolutePath) {
                packageFile.copyTo(targetArchive, overwrite = true)
            }
        } else {
            targetArchive = packageFile
        }

        val record = InstalledBotRecord(
            botId = bot.id,
            name = bot.name,
            version = bot.version,
            packagePath = targetArchive.absolutePath,
            runtimeType = bot.packageInfo.runtimeType,
            installedAt = System.currentTimeMillis()
        )

        installedMap[bot.id] = record
        saveToDisk()
    }

    @Synchronized
    override fun uninstallBot(botId: String): Result<Unit> = runCatching {
        val record = installedMap.remove(botId)
        if (record != null) {
            val file = File(record.packagePath)
            if (file.exists()) {
                file.delete()
            }
            saveToDisk()
        }
    }

    @Synchronized
    override fun getInstalledBots(): List<InstalledBotRecord> {
        return installedMap.values.toList()
    }

    private fun loadFromDisk() {
        if (storageFile != null && storageFile.exists() && storageFile.length() > 0) {
            try {
                val text = storageFile.readText()
                val list = json.decodeFromString(ListSerializer(InstalledBotRecord.serializer()), text)
                list.forEach { installedMap[it.botId] = it }
            } catch (_: Exception) {
                // Ignore corrupt file
            }
        }
    }

    private fun saveToDisk() {
        if (storageFile != null) {
            try {
                storageFile.parentFile?.mkdirs()
                val text = json.encodeToString(ListSerializer(InstalledBotRecord.serializer()), installedMap.values.toList())
                storageFile.writeText(text)
            } catch (_: Exception) {
                // Ignore disk write failure
            }
        }
    }
}
