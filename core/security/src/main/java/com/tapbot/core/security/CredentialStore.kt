package com.tapbot.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production interface for on-device secret storage.
 *
 * CORE RULES:
 * 1. User secrets (Telegram bot tokens, external API keys) remain strictly ON-DEVICE.
 * 2. User secrets are NEVER transmitted to the Bot Store backend.
 * 3. Secrets are encrypted at rest via hardware-backed Android Keystore keys (AES-256-GCM).
 */
interface CredentialStore {
    /**
     * Securely stores a secret credential for a specific bot.
     */
    suspend fun saveCredential(botId: String, key: String, secretValue: String)

    /**
     * Retrieves a decrypted secret credential for a specific bot.
     */
    suspend fun getCredential(botId: String, key: String): String?

    /**
     * Deletes a specific secret credential for a bot.
     */
    suspend fun deleteCredential(botId: String, key: String)

    /**
     * Checks if a specific credential exists and is non-empty for a bot.
     */
    suspend fun hasCredential(botId: String, key: String): Boolean

    /**
     * Retrieves all configured decrypted credentials for a given bot.
     */
    suspend fun getAllCredentials(botId: String): Map<String, String>

    /**
     * Removes all stored credentials for a specific bot (e.g. upon uninstall or credential reset).
     */
    suspend fun deleteCredentials(botId: String)

    /**
     * Checks if all required keys are present and non-blank for a given bot.
     */
    suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean

    // -------------------------------------------------------------------------
    // Single-key convenience overloads (defaults to "default" bot ID)
    // -------------------------------------------------------------------------
    suspend fun saveCredential(key: String, secretValue: String) = saveCredential("default", key, secretValue)
    suspend fun getCredential(key: String): String? = getCredential("default", key)
    suspend fun deleteCredential(key: String) = deleteCredential("default", key)
    suspend fun hasCredential(key: String): Boolean = hasCredential("default", key)

    // -------------------------------------------------------------------------
    // POC backward compatibility methods
    // -------------------------------------------------------------------------
    suspend fun saveToken(token: String) = saveCredential("poc_bot", "telegram_bot_token", token)
    suspend fun getToken(): String? = getCredential("poc_bot", "telegram_bot_token")
    suspend fun clearToken() = deleteCredential("poc_bot", "telegram_bot_token")
    suspend fun hasToken(): Boolean = hasCredential("poc_bot", "telegram_bot_token")
}

/**
 * Production Android Keystore implementation of [CredentialStore].
 * Encrypts secrets using AES-256-GCM backed by the Android Keystore system.
 */
open class KeystoreCredentialStore(
    private val context: Context,
    private val prefsFileName: String = PREFS_FILENAME
) : CredentialStore {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            prefsFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveCredential(botId: String, key: String, secretValue: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(buildStorageKey(botId, key), secretValue.trim())
                .apply()
        }

    override suspend fun getCredential(botId: String, key: String): String? =
        withContext(Dispatchers.IO) {
            val fullKey = buildStorageKey(botId, key)
            val value = sharedPreferences.getString(fullKey, null)
            if (value != null && value.isNotBlank()) {
                value
            } else if (botId == "poc_bot" && key == "telegram_bot_token") {
                // Check legacy un-prefixed key if present
                sharedPreferences.getString("telegram_bot_token", null)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

    override suspend fun deleteCredential(botId: String, key: String) =
        withContext(Dispatchers.IO) {
            val fullKey = buildStorageKey(botId, key)
            val editor = sharedPreferences.edit().remove(fullKey)
            if (botId == "poc_bot" && key == "telegram_bot_token") {
                editor.remove("telegram_bot_token")
            }
            editor.apply()
        }

    override suspend fun hasCredential(botId: String, key: String): Boolean =
        withContext(Dispatchers.IO) {
            !getCredential(botId, key).isNullOrBlank()
        }

    override suspend fun getAllCredentials(botId: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val prefix = "${botId}_"
            val result = mutableMapOf<String, String>()
            val allEntries = sharedPreferences.all
            for ((k, v) in allEntries) {
                if (k.startsWith(prefix) && v is String && v.isNotBlank()) {
                    val credentialKey = k.removePrefix(prefix)
                    result[credentialKey] = v
                }
            }
            result
        }

    override suspend fun deleteCredentials(botId: String) =
        withContext(Dispatchers.IO) {
            val prefix = "${botId}_"
            val editor = sharedPreferences.edit()
            for (k in sharedPreferences.all.keys) {
                if (k.startsWith(prefix)) {
                    editor.remove(k)
                }
            }
            if (botId == "poc_bot") {
                editor.remove("telegram_bot_token")
            }
            editor.apply()
        }

    override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            requiredKeys.all { key ->
                val value = getCredential(botId, key)
                !value.isNullOrBlank()
            }
        }

    private fun buildStorageKey(botId: String, key: String): String = "${botId}_$key"

    companion object {
        const val PREFS_FILENAME = "tapbot_secure_credentials"
    }
}
