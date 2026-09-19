package com.tapbot.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android Keystore implementation of [SecureCredentialStore].
 * Encrypts secrets using AES256-GCM backed by the Android Keystore system.
 */
class KeystoreSecureCredentialStore(
    private val context: Context
) : SecureCredentialStore {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveCredential(botId: String, key: String, secretValue: String) =
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(buildStorageKey(botId, key), secretValue)
                .apply()
        }

    override suspend fun getCredential(botId: String, key: String): String? =
        withContext(Dispatchers.IO) {
            sharedPreferences.getString(buildStorageKey(botId, key), null)
        }

    override suspend fun getAllCredentials(botId: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val prefix = "${botId}_"
            val result = mutableMapOf<String, String>()
            val allEntries = sharedPreferences.all
            for ((k, v) in allEntries) {
                if (k.startsWith(prefix) && v is String) {
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
            editor.apply()
        }

    override suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            requiredKeys.all { key ->
                val value = sharedPreferences.getString(buildStorageKey(botId, key), null)
                !value.isNullOrBlank()
            }
        }

    private fun buildStorageKey(botId: String, key: String): String = "${botId}_$key"

    companion object {
        private const val PREFS_FILENAME = "tapbot_secure_vault"
    }
}
