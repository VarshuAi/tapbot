package com.tapbot.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Result of a package verification check.
 */
data class PackageVerificationResult(
    val isValid: Boolean,
    val computedSha256: String,
    val isSignatureVerified: Boolean = false,
    val details: String = "Verification successful"
)

/**
 * Pluggable contract for verifying bot package integrity and authenticity.
 * Designed to support future cryptographic digital signatures (e.g. Ed25519, RSA).
 */
interface PackageVerifier {
    /**
     * Verifies the package archive at [packageFile] against [expectedSha256]
     * and optional future cryptographic [signature].
     */
    suspend fun verifyPackage(
        packageFile: File,
        expectedSha256: String,
        signature: String? = null
    ): Result<PackageVerificationResult>
}

/**
 * Cryptographic digital signature verifier hook for future package signing.
 */
interface DigitalSignatureVerifier {
    fun verifySignature(
        packageFile: File,
        signature: String,
        publicKeyPem: String
    ): Result<Boolean>
}

/**
 * Production implementation verifying SHA-256 integrity of downloaded `.botpkg` archives.
 */
class Sha256PackageVerifier(
    private val signatureVerifier: DigitalSignatureVerifier? = null
) : PackageVerifier {

    override suspend fun verifyPackage(
        packageFile: File,
        expectedSha256: String,
        signature: String?
    ): Result<PackageVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!packageFile.exists() || packageFile.length() == 0L) {
                throw SecurityException("Package file does not exist or is empty: ${packageFile.name}")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(packageFile).use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }

            val computed = digest.digest().joinToString("") { "%02x".format(it) }

            if (!computed.equals(expectedSha256.trim(), ignoreCase = true)) {
                packageFile.delete() // Purge corrupt or tampered package immediately
                throw SecurityException(
                    "Integrity verification failed! Expected SHA-256: $expectedSha256, Computed: $computed"
                )
            }

            // Optional future digital signature check
            var signatureValid = false
            if (signature != null && signatureVerifier != null) {
                val sigResult = signatureVerifier.verifySignature(packageFile, signature, "")
                if (sigResult.isFailure || sigResult.getOrNull() != true) {
                    packageFile.delete()
                    throw SecurityException("Cryptographic digital signature verification failed for ${packageFile.name}")
                }
                signatureValid = true
            }

            PackageVerificationResult(
                isValid = true,
                computedSha256 = computed,
                isSignatureVerified = signatureValid,
                details = "SHA-256 verified successfully ($computed)"
            )
        }
    }
}

/**
 * Anti-downgrade security enforcement engine.
 * Prevents malicious rollback attacks where an attacker attempts to replace an installed bot
 * with an older, vulnerable version.
 */
object DowngradeAttackChecker {

    /**
     * Checks whether [candidateVersion] represents a downgrade relative to [currentInstalledVersion].
     * Parses standard SemVer tokens (major.minor.patch).
     *
     * @return true if candidateVersion is older than currentInstalledVersion (downgrade detected).
     */
    fun isDowngrade(currentInstalledVersion: String?, candidateVersion: String): Boolean {
        if (currentInstalledVersion.isNullOrBlank()) return false
        if (candidateVersion.isBlank()) return true

        val currentParts = parseVersion(currentInstalledVersion)
        val candidateParts = parseVersion(candidateVersion)

        for (i in 0 until maxOf(currentParts.size, candidateParts.size)) {
            val curr = currentParts.getOrElse(i) { 0 }
            val cand = candidateParts.getOrElse(i) { 0 }
            if (cand < curr) return true
            if (cand > curr) return false
        }

        return false // Same version is not a downgrade
    }

    private fun parseVersion(version: String): List<Int> {
        val sanitized = version.trim().removePrefix("v").split("-")[0]
        return sanitized.split(".").mapNotNull { it.toIntOrNull() }
    }
}
