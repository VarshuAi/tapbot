package com.tapbot.core.security

import android.content.Context

/**
 * Android Keystore implementation of [SecureCredentialStore].
 * Encrypts secrets using AES256-GCM backed by the Android Keystore system.
 * Subclasses [KeystoreCredentialStore] for full backwards compatibility.
 */
class KeystoreSecureCredentialStore(
    context: Context,
    prefsFileName: String = PREFS_FILENAME
) : KeystoreCredentialStore(context, prefsFileName), SecureCredentialStore {

    companion object {
        const val PREFS_FILENAME = "tapbot_secure_vault"
    }
}
