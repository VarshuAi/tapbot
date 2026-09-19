package com.tapbot.core.security

/**
 * Secure on-device credential storage interface.
 * User Telegram Bot Tokens and sensitive API keys must NEVER leave the device
 * or be transmitted to the catalog backend.
 */
interface SecureCredentialStore {
    /**
     * Securely stores a credential (e.g. Telegram Bot Token or external API key)
     * encrypted with hardware-backed Android Keystore keys.
     */
    suspend fun saveCredential(botId: String, key: String, secretValue: String)

    /**
     * Retrieves the decrypted credential for a given bot and key.
     */
    suspend fun getCredential(botId: String, key: String): String?

    /**
     * Retrieves all configured credentials for a given bot.
     */
    suspend fun getAllCredentials(botId: String): Map<String, String>

    /**
     * Removes all stored credentials for a specific bot (e.g. upon uninstall).
     */
    suspend fun deleteCredentials(botId: String)

    /**
     * Checks if all required keys are present for a given bot.
     */
    suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean
}
