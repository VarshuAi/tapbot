package com.tapbot.core.network

import com.tapbot.core.model.SendMessagePayload
import com.tapbot.core.model.TelegramApiResponse
import com.tapbot.core.model.TelegramUpdate
import com.tapbot.core.model.TelegramUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for communicating with the official Telegram Bot API via HTTPS.
 * Executes on the user's Android device.
 */
open class TelegramApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Verifies the bot token and returns information about the bot.
     */
    open suspend fun getMe(token: String): Result<TelegramUser> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/bot$token/getMe"
            val request = Request.Builder().url(url).get().build()
            val body = client.newCall(request).execute().use { response ->
                response.body?.string().orEmpty()
            }
            val apiResponse = json.decodeFromString<TelegramApiResponse<TelegramUser>>(body)
            if (apiResponse.ok) {
                apiResponse.result ?: throw IOException("Empty user object returned by Telegram API")
            } else {
                throw IOException(apiResponse.description ?: "Unknown Telegram API error")
            }
        }
    }

    /**
     * Long-polling request to fetch new updates.
     * Uses Telegram's long-polling HTTP mechanism.
     */
    open suspend fun getUpdates(
        token: String,
        offset: Long? = null,
        timeoutSeconds: Int = 30
    ): Result<List<TelegramUpdate>> = runCatching {
        withContext(Dispatchers.IO) {
            val offsetParam = if (offset != null) "&offset=$offset" else ""
            val url = "$BASE_URL/bot$token/getUpdates?timeout=$timeoutSeconds$offsetParam"
            val request = Request.Builder().url(url).get().build()

            val body = client.newCall(request).execute().use { response ->
                response.body?.string().orEmpty()
            }
            val apiResponse = json.decodeFromString<TelegramApiResponse<List<TelegramUpdate>>>(body)
            if (apiResponse.ok) {
                apiResponse.result.orEmpty()
            } else {
                throw IOException(apiResponse.description ?: "getUpdates error")
            }
        }
    }

    /**
     * Sends a text message to a chat or channel.
     */
    open suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        parseMode: String? = null
    ): Result<Boolean> = runCatching {
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/bot$token/sendMessage"
            val payload = SendMessagePayload(chatId = chatId, text = text, parseMode = parseMode)
            val requestBody = json.encodeToString(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).post(requestBody).build()

            val body = client.newCall(request).execute().use { response ->
                response.body?.string().orEmpty()
            }
            val apiResponse = json.decodeFromString<TelegramApiResponse<Map<String, String>>>(body)
            if (apiResponse.ok) {
                true
            } else {
                throw IOException(apiResponse.description ?: "sendMessage error")
            }
        }
    }

    companion object {
        private const val BASE_URL = "https://api.telegram.org"
    }
}
