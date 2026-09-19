# TapBot Security Architecture & Threat Model

This document outlines the security architecture, threat model, and cryptographic controls implemented across **TapBot** (Bot Store & On-Device Telegram Bot Runner).

---

## 1. Core Principle: Zero Backend Knowledge

> **CRITICAL ACCEPTANCE CRITERION**  
> **A Telegram bot token or API secret entered by a user NEVER leaves the Android device.**  
> Secrets are strictly stored in hardware-backed storage and are never transmitted to Cloudflare Workers, Cloudflare D1, Cloudflare R2, or any remote server controlled by TapBot.

```
+-------------------------------------------------------------------------+
|                              ANDROID DEVICE                             |
|                                                                         |
|   +--------------------+     Secure Input      +--------------------+   |
|   |  Credential UI     | --------------------> |  Android Keystore  |   |
|   |  (FLAG_SECURE ON)  |                       |  (AES-256-GCM)     |   |
|   +--------------------+                       +--------------------+   |
|             |                                            |              |
|             | (NEVER SENT)                               |              |
|             x                                            v              |
|             x                                  +--------------------+   |
|             x                                  |  Local Bot Runner  |   |
|             x                                  |  (Isolated Loop)   |   |
|             x                                  +--------------------+   |
|             x                                            |              |
+-------------x--------------------------------------------|--------------+
              x                                            | HTTPS
              x                                            v
+-----------------------------+                  +--------------------+
|   TapBot Cloudflare Backend |                  | Telegram Bot API   |
|   (Catalog & Metadata ONLY) |                  | (Direct from phone)|
+-----------------------------+                  +--------------------+
```

The TapBot backend only serves public catalog metadata, versions, changelogs, and package binaries. The backend does not maintain user accounts, user database tables, or credential endpoints.

---

## 2. Threat Model Analysis

### 2.1 Malicious Package Replacement
* **Threat:** An attacker replaces legitimate `.botpkg` distribution packages with compromised code intended to exfiltrate tokens or execute malicious payloads.
* **Mitigation:**
  * **Cryptographic Hash Verification:** Every package release is registered in Cloudflare D1 with a SHA-256 checksum calculated at build time.
  * **Streaming Integrity Verification:** During download, `Sha256PackageVerifier` streams the bytes through `MessageDigest("SHA-256")`. If the computed digest does not match the metadata checksum byte-for-byte, the file is rejected immediately and deleted from disk before any installation or execution can occur.
  * **Future Digital Signing:** The architecture is designed with the `DigitalSignatureVerifier` hook, allowing packages to be verified against the publisher's Ed25519/RSA-PSS public key.

### 2.2 Compromised CDN (Cloudflare R2)
* **Threat:** The storage CDN hosting `.botpkg` binaries is breached, and packages are altered or swapped directly on the CDN.
* **Mitigation:**
  * Packages fetched from the CDN are checked against the SHA-256 digest retrieved from the catalog API database.
  * Because the download verification is performed on the Android client before local decompression, tampered binaries on the CDN will fail the client-side hash check and be purged immediately.

### 2.3 Backend Compromise (Cloudflare Workers / D1 Database)
* **Threat:** An attacker gains full administrative access to the TapBot Cloudflare Worker environment and D1 SQLite database.
* **Impact & Mitigation:**
  * **Zero Token Exposure:** Since user credentials are never transmitted or stored on the backend, a full breach of the database and worker environment yields **zero user bot tokens or user secrets**.
  * **Attacker Attempting to Push Malicious Hashes:** If an attacker alters the SHA-256 in D1 to match a malicious binary on R2, client-side downgrade attack checks (`DowngradeAttackChecker`) prevent rolling back to compromised older versions. Furthermore, package signing (Ed25519) will ensure that only packages signed with the offline publisher key are accepted.

### 2.4 Token Leakage Vectors (Network, IPC, Background Service)
* **Threat:** Tokens are intercepted via unencrypted network transit, exported Android IPC components, or ambient memory leaks.
* **Mitigation:**
  * **Local Injection:** Bot credentials are read from `CredentialStore` and injected directly into the local runtime process/coroutine environment.
  * **Android Component Security:** `BotForegroundService` is configured with `android:exported="false"` in `AndroidManifest.xml`. External applications cannot bind to or command the service.
  * **Direct TLS to Telegram:** Outbound bot traffic goes directly from the local device to `https://api.telegram.org` over TLS 1.3.

### 2.5 Logs Containing Secrets (Logcat, Crash Reports, UI)
* **Threat:** Sensitive tokens and API keys are printed in Android logcat, crash traces, analytics, or UI console panes.
* **Mitigation:**
  * **`SecretRedactor` Engine:** All system and bot runtime logs pass through `SecretRedactor` before being written to disk or shown in the UI:
    * Telegram Bot Tokens (`\d{8,12}:[a-zA-Z0-9_-]{35,50}`) $\rightarrow$ `[REDACTED_TELEGRAM_TOKEN]`
    * Telegram API URLs (`/bot<token>/...`) $\rightarrow$ `/bot[REDACTED_TELEGRAM_TOKEN]/...`
    * OpenAI Keys (`sk-[a-zA-Z0-9_-]{20,}`) $\rightarrow$ `[REDACTED_OPENAI_KEY]`
    * Anthropic Keys (`sk-ant-[a-zA-Z0-9_-]{20,}`) $\rightarrow$ `[REDACTED_ANTHROPIC_KEY]`
    * Bearer Authorization Headers $\rightarrow$ `Bearer [REDACTED_BEARER_TOKEN]`
    * Dynamic In-Memory Secret Registry: Any custom secret saved by the user is registered in memory with `SecretRedactor.registerSecret(value)` and scrubbed with `[REDACTED_SECRET]`.

### 2.6 Local Filesystem Exposure (Rooted Devices, Backups)
* **Threat:** An attacker gains access to physical device storage, extracts ADB backups, or accesses the application sandbox on a rooted device.
* **Mitigation:**
  * **Hardware-Backed Keystore:** Credentials are encrypted using `EncryptedSharedPreferences` backed by Android Keystore's `MasterKey` (`AES256_GCM_SPEC`).
  * **Key Derivation in Hardware:** The master key is generated inside the device's Trusted Execution Environment (TEE) or StrongBox Keymaster. The raw cryptographic keys never leave the hardware module.
  * **Backup Disabled:** `android:allowBackup="false"` is set in `AndroidManifest.xml` to prevent credential extraction via `adb backup`.

### 2.7 Downgrade Attacks
* **Threat:** An attacker tricks the app into "upgrading" a bot with an older version that has known security vulnerabilities.
* **Mitigation:**
  * **`DowngradeAttackChecker`:** Validates incoming package versions against currently installed versions using Semantic Versioning (SemVer: `MAJOR.MINOR.PATCH`).
  * If the incoming package version is lower than the installed version, the installation is aborted with `SecurityException("Downgrade attack detected")`.

### 2.8 Screen Capture & Task Switcher Exposure
* **Threat:** Background spyware captures screenshots of the credential input field, or the OS task switcher displays a plaintext preview of the user's Telegram token.
* **Mitigation:**
  * **`FLAG_SECURE` Enforced:** `SecurityWindowManager.setSecureFlag(activity, true)` sets `WindowManager.LayoutParams.FLAG_SECURE` whenever the `BotDetailScreen` is active.
  * Screenshots, screen recordings, and OS recent app thumbnail captures are blocked by the Android window compositor.

---

## 3. Cryptographic Storage Implementation

The primary credential storage interface is [`CredentialStore`](file:///c:/Users/varshan/Downloads/projects/tapbot/core/security/src/main/java/com/tapbot/core/security/CredentialStore.kt):

```kotlin
interface CredentialStore {
    suspend fun saveCredential(botId: String, key: String, secretValue: String)
    suspend fun getCredential(botId: String, key: String): String?
    suspend fun deleteCredential(botId: String, key: String)
    suspend fun hasCredential(botId: String, key: String): Boolean
    suspend fun getAllCredentials(botId: String): Map<String, String>
    suspend fun deleteCredentials(botId: String)
    suspend fun hasRequiredCredentials(botId: String, requiredKeys: List<String>): Boolean
}
```

The production implementation [`KeystoreCredentialStore`](file:///c:/Users/varshan/Downloads/projects/tapbot/core/security/src/main/java/com/tapbot/core/security/KeystoreCredentialStore.kt) creates an isolated `EncryptedSharedPreferences` container per bot (`tapbot_secure_creds_${botId}`) encrypted with AES-256-GCM.

---

## 4. Verification & Testing

The security guarantees are verified by the automated test suite:

| Test Class | Scope |
| :--- | :--- |
| [`BotCredentialSecurityTest.kt`](file:///c:/Users/varshan/Downloads/projects/tapbot/feature/botdetail/src/test/java/com/tapbot/feature/botdetail/BotCredentialSecurityTest.kt) | Verifies user tokens never reach backend, missing credentials prevent start, deletion wipes storage, logs are redacted |
| [`CredentialStoreTest.kt`](file:///c:/Users/varshan/Downloads/projects/tapbot/core/security/src/test/java/com/tapbot/core/security/CredentialStoreTest.kt) | Unit tests for Keystore credential storage lifecycle and isolation |
| [`SecretRedactorTest.kt`](file:///c:/Users/varshan/Downloads/projects/tapbot/core/security/src/test/java/com/tapbot/core/security/SecretRedactorTest.kt) | Unit tests verifying regex scrubbing of Telegram tokens, API keys, and registered secrets |
| [`PackageVerifierTest.kt`](file:///c:/Users/varshan/Downloads/projects/tapbot/core/network/src/test/java/com/tapbot/core/network/PackageVerifierTest.kt) | Streaming SHA-256 validation, corrupt package deletion, downgrade protection |

---

## 5. Security Checklist for Bot Authors

1. Bots must NEVER accept hardcoded tokens or secrets in package assets or code.
2. Bots must declare all required credentials in `manifest.json` under the `credentials` array with `secret: true`.
3. Bots must read credentials exclusively from environment variables or runtime configuration files supplied by the local runner.
4. Bots must NEVER transmit user credentials to external analytics or telemetry services.
