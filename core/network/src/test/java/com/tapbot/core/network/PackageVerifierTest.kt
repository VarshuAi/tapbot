package com.tapbot.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PackageVerifierTest {

    private fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun verifyPackage_matchingSha256_succeeds() = runTest {
        val tempDir = File.createTempFile("verifier_test", "").apply { delete(); mkdirs() }
        val testFile = File(tempDir, "test.botpkg")
        val content = "GENUINE_PACKAGE_CONTENT_12345".toByteArray()
        testFile.writeBytes(content)
        val expectedHash = sha256Of(content)

        val verifier = Sha256PackageVerifier()
        val result = verifier.verifyPackage(testFile, expectedHash)

        assertTrue(result.isSuccess)
        val verification = result.getOrThrow()
        assertTrue(verification.isValid)
        assertEquals(expectedHash, verification.computedSha256)
        assertTrue(testFile.exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun verifyPackage_tamperedFile_failsAndDeletesFile() = runTest {
        val tempDir = File.createTempFile("verifier_tamper_test", "").apply { delete(); mkdirs() }
        val testFile = File(tempDir, "tampered.botpkg")
        testFile.writeText("MALICIOUS_TAMPERED_CONTENT")
        val legitHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val verifier = Sha256PackageVerifier()
        val result = verifier.verifyPackage(testFile, legitHash)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse("Tampered file must be deleted upon failed verification", testFile.exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun downgradeAttackChecker_correctlyDetectsRollbacks() {
        // Downgrades
        assertTrue(DowngradeAttackChecker.isDowngrade("1.2.0", "1.1.9"))
        assertTrue(DowngradeAttackChecker.isDowngrade("2.0.0", "1.9.9"))
        assertTrue(DowngradeAttackChecker.isDowngrade("1.5.3", "1.5.2"))

        // Upgrades
        assertFalse(DowngradeAttackChecker.isDowngrade("1.0.0", "1.0.1"))
        assertFalse(DowngradeAttackChecker.isDowngrade("1.0.0", "1.1.0"))
        assertFalse(DowngradeAttackChecker.isDowngrade("1.9.0", "2.0.0"))

        // Same version
        assertFalse(DowngradeAttackChecker.isDowngrade("1.0.0", "1.0.0"))

        // Fresh install (no current version installed)
        assertFalse(DowngradeAttackChecker.isDowngrade(null, "1.0.0"))
    }
}
