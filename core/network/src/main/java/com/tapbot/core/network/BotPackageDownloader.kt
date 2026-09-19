package com.tapbot.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Contract for downloading and verifying bot package (.botpkg) archives.
 */
interface BotPackageDownloader {
    /**
     * Downloads a package from [packageUrl], calculates and validates its SHA-256 checksum against [expectedSha256],
     * and writes it to [targetFile].
     *
     * Emits download progress between 0.0f and 1.0f via [onProgress].
     * Avoids re-downloading if an identical package is already cached locally.
     */
    suspend fun downloadPackage(
        packageUrl: String,
        expectedSha256: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File>
}

/**
 * Production implementation using OkHttp with chunked streaming and on-the-fly SHA-256 verification.
 */
class OkHttpBotPackageDownloader(
    private val client: OkHttpClient = OkHttpClient(),
    private val cacheDir: File? = null,
    private val packageVerifier: PackageVerifier = Sha256PackageVerifier()
) : BotPackageDownloader {

    override suspend fun downloadPackage(
        packageUrl: String,
        expectedSha256: String,
        targetFile: File,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Check local cache to avoid duplicate downloads
            val cachedArchive = cacheDir?.let { File(it, "$expectedSha256.botpkg") }
            if (cachedArchive != null && cachedArchive.exists() && cachedArchive.length() > 0) {
                val cacheVerification = packageVerifier.verifyPackage(cachedArchive, expectedSha256)
                if (cacheVerification.isSuccess) {
                    if (cachedArchive.absolutePath != targetFile.absolutePath) {
                        targetFile.parentFile?.mkdirs()
                        cachedArchive.copyTo(targetFile, overwrite = true)
                    }
                    onProgress(1.0f)
                    return@runCatching targetFile
                } else {
                    cachedArchive.delete() // Corrupt cache entry
                }
            }

            // 2. Ensure target directories exist
            targetFile.parentFile?.mkdirs()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp_${System.currentTimeMillis()}")

            val request = Request.Builder()
                .url(packageUrl)
                .header("Accept", "application/octet-stream")
                .get()
                .build()

            val digest = MessageDigest.getInstance("SHA-256")

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download package: HTTP ${response.code} ${response.message}")
                    }

                    val responseBody = response.body ?: throw IOException("Empty response body from $packageUrl")
                    val totalBytes = responseBody.contentLength()
                    var bytesReadTotal = 0L

                    responseBody.byteStream().use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                outputStream.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                bytesReadTotal += read

                                if (totalBytes > 0) {
                                    val progress = (bytesReadTotal.toFloat() / totalBytes.toFloat()).coerceIn(0.0f, 0.99f)
                                    onProgress(progress)
                                }
                            }
                            outputStream.flush()
                        }
                    }
                }

                // 3. Verify Package Integrity using PackageVerifier
                packageVerifier.verifyPackage(tempFile, expectedSha256).getOrThrow()

                // 4. Move temp file to final destination
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // 5. Populate cache
                if (cachedArchive != null && cachedArchive.absolutePath != targetFile.absolutePath) {
                    cachedArchive.parentFile?.mkdirs()
                    targetFile.copyTo(cachedArchive, overwrite = true)
                }

                onProgress(1.0f)
                targetFile
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                throw e
            }
        }
    }
}
