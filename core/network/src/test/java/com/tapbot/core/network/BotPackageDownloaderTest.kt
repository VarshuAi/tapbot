package com.tapbot.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class BotPackageDownloaderTest {

    private fun sha256Of(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun downloadPackage_successfulDownloadAndVerification() = runTest {
        val samplePayload = "SIMULATED_BOTPKG_ARCHIVE_DATA_123456789".toByteArray()
        val expectedHash = sha256Of(samplePayload)

        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(samplePayload.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val tempDir = File.createTempFile("pkg_test", "").apply { delete(); mkdirs() }
        val targetFile = File(tempDir, "test_bot.botpkg")

        val progressUpdates = mutableListOf<Float>()
        val downloader = OkHttpBotPackageDownloader(client = client, cacheDir = tempDir)

        val result = downloader.downloadPackage(
            packageUrl = "https://mock.cdn/test.botpkg",
            expectedSha256 = expectedHash,
            targetFile = targetFile,
            onProgress = { progressUpdates.add(it) }
        )

        assertTrue(result.isSuccess)
        val downloaded = result.getOrThrow()
        assertTrue(downloaded.exists())
        assertEquals(samplePayload.size.toLong(), downloaded.length())
        assertTrue("Progress should finish at 1.0f", progressUpdates.last() == 1.0f)

        tempDir.deleteRecursively()
    }

    @Test
    fun downloadPackage_corruptedChecksumFailsAndDeletesTempFile() = runTest {
        val samplePayload = "ACTUAL_PAYLOAD_CONTENT".toByteArray()
        val wrongExpectedHash = "0000000000000000000000000000000000000000000000000000000000000000"

        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(samplePayload.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val tempDir = File.createTempFile("pkg_corrupt_test", "").apply { delete(); mkdirs() }
        val targetFile = File(tempDir, "corrupt_bot.botpkg")

        val downloader = OkHttpBotPackageDownloader(client = client, cacheDir = tempDir)

        val result = downloader.downloadPackage(
            packageUrl = "https://mock.cdn/corrupt.botpkg",
            expectedSha256 = wrongExpectedHash,
            targetFile = targetFile
        )

        assertTrue("Expected download to fail on checksum mismatch", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)

        // Ensure corrupted file was deleted and not left on disk
        assertFalse(targetFile.exists())
        val tmpFiles = tempDir.listFiles { _, name -> name.contains(".tmp") }
        assertTrue("Temp files must be cleaned up after failure", tmpFiles == null || tmpFiles.isEmpty())

        tempDir.deleteRecursively()
    }

    @Test
    fun downloadPackage_cacheHitSkipsNetworkCall() = runTest {
        val samplePayload = "PRE_CACHED_ARCHIVE_DATA".toByteArray()
        val expectedHash = sha256Of(samplePayload)

        val tempDir = File.createTempFile("pkg_cache_hit_test", "").apply { delete(); mkdirs() }
        val cacheFile = File(tempDir, "$expectedHash.botpkg")
        cacheFile.writeBytes(samplePayload)

        var networkCalls = 0
        val interceptor = Interceptor { chain ->
            networkCalls++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(samplePayload.toResponseBody("application/octet-stream".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val targetFile = File(tempDir, "output_bot.botpkg")
        val downloader = OkHttpBotPackageDownloader(client = client, cacheDir = tempDir)

        val result = downloader.downloadPackage(
            packageUrl = "https://mock.cdn/test.botpkg",
            expectedSha256 = expectedHash,
            targetFile = targetFile
        )

        assertTrue(result.isSuccess)
        assertEquals(0, networkCalls) // Network was never hit because cache matched!
        assertTrue(targetFile.exists())
        assertEquals(samplePayload.size.toLong(), targetFile.length())

        tempDir.deleteRecursively()
    }
}
