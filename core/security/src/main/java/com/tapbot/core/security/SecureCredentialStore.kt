package com.tapbot.core.security

/**
 * Secure on-device credential storage interface.
 * User Telegram Bot Tokens and sensitive API keys must NEVER leave the device
 * or be transmitted to the catalog backend.
 *
 * Extends [CredentialStore] to ensure unified API contracts across all modules.
 */
interface SecureCredentialStore : CredentialStore
