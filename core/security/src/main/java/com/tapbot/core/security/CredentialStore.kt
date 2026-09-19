package com.tapbot.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Interface for on-device secret storage.
 * In accordance with Phase 2 requirements, guarantees that tokens:
 * - Are never hardcoded
 * - Are never committed to Git
 * - Are never sent to any backend
 * - Are encrypted on-device via Android Keystore
 */
interface CredentialStore {
    suspend fun saveToken(token: String)
    suspend fun getToken(): String?
    suspend fun clearToken()
    suspend fun hasToken(): Boolean
}

/**
 * Android Keystore implementation of [CredentialStore].
 */
class KeystoreCredentialStore(
    context: Context
) : CredentialStore {

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun saveToken(token: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putString(KEY_TELEGRAM_TOKEN, token.trim())
            .apply()
    }

    override suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(KEY_TELEGRAM_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override suspend fun clearToken() = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .remove(KEY_TELEGRAM_TOKEN)
            .apply()
    }

    override suspend fun hasToken(): Boolean = withContext(Dispatchers.IO) {
        !getToken().isNullOrBlank()
    }

    companion object {
        private const val PREFS_NAME = "tapbot_poc_credentials"
        private const val KEY_TELEGRAM_TOKEN = "telegram_bot_token"
    }
}
