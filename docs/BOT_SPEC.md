# TapBot Official Bot Package Specification (v1.0)

## 1. Overview & Platform Principles

TapBot is a curated **Bot Store + Local Telegram Bot Runner** for Android.
Every bot published to the platform adheres to a standardized, hermetic package format.

### Key Tenets
1. **Curated Standard, Not Arbitrary Repositories**: Bots are distributed as pre-validated, signed `.botpkg` archives. TapBot does not clone or compile arbitrary git repositories on user devices.
2. **On-Device Execution**: The Android device runs the bot locally via the native Android Runtime (ART) coroutine engine and a resilient Foreground Service.
3. **No Phantom Capabilities**: The specification only defines permissions and capabilities that the Android runtime environment can strictly enforce.
4. **Dynamic Credential Discovery**: Bot packages define their required and optional credential schemas via `manifest.json`. The Android application discovers and dynamically generates user configuration screens without hardcoded bot-specific code.
5. **Zero Token Transmission**: User Telegram bot tokens and API credentials remain encrypted in the Android device's hardware-backed Keystore (`AES-256-GCM`).

---

## 2. Directory & Archive Structure

Every bot package directory before archiving (and the unpacked root of every `.botpkg` archive) must strictly follow this structure:

```
bot/
├── manifest.json        # Formal metadata, credentials schema, and runtime config
├── entrypoint           # Entry class definition or binary execution artifact
├── dependencies/        # Supporting libraries, bytecode modules, or configuration
└── assets/              # Visual assets, icon, and promotional media
    └── icon.png         # Square icon (minimum 128x128 PNG)
```

### Required Files
- **`manifest.json`** *(required)*: The machine-readable manifest conforming to the v1 JSON schema.
- **`entrypoint`** *(required)*: Class identifier (for native ART runtimes, e.g. `com.tapbot.bots.MyBotRunner`) or entry artifact located in the package.
- **`assets/icon.png`** *(required)*: High-resolution square PNG icon representing the bot in the catalog.

---

## 3. Manifest Specification (`manifest.json`)

The `manifest.json` file is the contract between the bot publisher, the Cloudflare catalog backend, and the Android client.

### Complete Conceptual Manifest Example

```json
{
  "$schema": "https://tapbot.dev/schemas/v1/manifest.json",
  "id": "music-controller-bot",
  "name": "Music Controller Bot",
  "version": "1.0.0",
  "description": "Control your local audio and streaming media directly via Telegram.",
  "longDescription": "Music Controller Bot runs locally in the background on your Android device. It connects via long-polling to the Telegram Bot API and handles commands like /play, /pause, /skip, and /volume, interfacing with media sessions.",
  "category": "media",
  "icon": "assets/icon.png",
  "runtime": "native_art",
  "entrypoint": "com.tapbot.bots.music.MusicBotRunner",
  "minimumRuntimeVersion": "1.0.0",
  "minimumAppVersion": 1,
  "credentials": [
    {
      "key": "bot_token",
      "label": "Telegram Bot Token",
      "description": "API token obtained from @BotFather on Telegram.",
      "required": true,
      "secret": true,
      "inputType": "password",
      "placeholder": "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ",
      "helpUrl": "https://t.me/BotFather"
    },
    {
      "key": "spotify_client_id",
      "label": "Spotify Client ID",
      "description": "Client ID from your Spotify Developer Dashboard for remote queueing.",
      "required": true,
      "secret": false,
      "inputType": "text",
      "placeholder": "your_spotify_client_id",
      "helpUrl": "https://developer.spotify.com/dashboard"
    },
    {
      "key": "default_volume",
      "label": "Default Volume (0-100)",
      "description": "Initial playback volume percentage on startup.",
      "required": false,
      "secret": false,
      "inputType": "number",
      "placeholder": "75"
    }
  ],
  "permissions": [
    "INTERNET",
    "FOREGROUND_SERVICE",
    "NOTIFICATIONS"
  ],
  "packageMetadata": {
    "author": "TapBot Publishing",
    "license": "Apache-2.0",
    "repository": "https://github.com/tapbot/music-controller-bot",
    "createdAt": "2026-09-19T00:00:00Z"
  }
}
```

---

## 4. Manifest Field Reference

| Field | Type | Required | Description | Validation Rule |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `string` | **Yes** | Unique identifier slug for the bot. | Lowercase alphanumeric and hyphens: `^[a-z0-9]+(-[a-z0-9]+)*$` |
| `name` | `string` | **Yes** | Human-readable display name. | 3 to 64 characters. |
| `version` | `string` | **Yes** | Semantic version string. | Strict SemVer: `^([0-9]+)\.([0-9]+)\.([0-9]+)$` |
| `description` | `string` | **Yes** | Short summary shown in catalog cards. | 10 to 256 characters. |
| `longDescription` | `string` | No | Markdown-formatted detailed overview. | Up to 4096 characters. |
| `category` | `string` | **Yes** | Primary catalog category. | One of: `utilities`, `productivity`, `media`, `automation`. |
| `icon` | `string` | **Yes** | Relative path to the icon asset. | File must exist in package (e.g. `assets/icon.png`). |
| `runtime` | `string` | **Yes** | Target execution runtime. | Must be a supported runtime: `native_art`. |
| `entrypoint` | `string` | **Yes** | Fully qualified class name or entry point. | Valid Java/Kotlin class identifier or package file path. |
| `minimumRuntimeVersion`| `string` | **Yes** | Minimum runner engine version required. | SemVer string (e.g. `1.0.0`). |
| `minimumAppVersion` | `integer`| **Yes** | Minimum Android `versionCode` required. | Positive integer `1+`. |
| `credentials` | `array` | **Yes** | Schema of required & optional credentials. | Array of `CredentialSpec` objects (see below). |
| `permissions` | `array` | **Yes** | Enforceable platform permissions. | Subset of valid platform permissions (see below). |
| `packageMetadata` | `object` | No | Publisher and build metadata. | Object with `author`, `license`, `repository`, `createdAt`. |

---

## 5. Credential Specification Schema

The `credentials` array defines all user-supplied configurations required to run the bot. The Android application dynamically iterates over these definitions to render the configuration form.

```json
{
  "key": "spotify_client_id",
  "label": "Spotify Client ID",
  "description": "Client ID from your Spotify Developer Dashboard",
  "required": true,
  "secret": false,
  "inputType": "text",
  "placeholder": "your_spotify_client_id",
  "helpUrl": "https://developer.spotify.com/dashboard"
}
```

### Credential Fields
- **`key`** *(string, required)*: Identifier used as the key in the encrypted Keystore (`^[a-z0-9_]+$`).
- **`label`** *(string, required)*: Form field label presented to the user.
- **`description`** *(string, optional)*: Explanatory text guiding the user where to obtain the credential.
- **`required`** *(boolean, default `true`)*: If `true`, the bot runner blocks execution until this field is filled.
- **`secret`** *(boolean, default `true`)*: If `true`, the UI obscures input with bullet masking and provides a show/hide toggle.
- **`inputType`** *(string, default `"text"`)*: Keyboard and validation hint:
  - `"text"`: Standard single-line text.
  - `"password"`: Masked credential / token.
  - `"number"`: Numerical input (e.g. port, volume, timeout).
  - `"url"`: Web/API URL (e.g. RSS feed, webhook endpoint).
- **`placeholder`** *(string, optional)*: Ghost text showing expected format.
- **`helpUrl`** *(string, optional)*: Clickable link opening external documentation or token generation portal.

---

## 6. Enforceable Platform Permissions

> [!CAUTION]
> **Do not declare permissions that the runtime cannot enforce.**
> TapBot operates under strict Android sandbox rules. Only permissions directly supported and managed by the `BotForegroundService` and runner engine are permitted:

1. **`INTERNET`**: Grants outbound HTTPS connectivity to Telegram servers (`api.telegram.org`) and third-party REST APIs.
2. **`FOREGROUND_SERVICE`**: Allows the bot process to remain active in the Android Foreground Service tier when the UI is closed or the screen is locked.
3. **`NOTIFICATIONS`**: Allows the bot service to present its persistent status notification and action controls (`Open`, `Stop`).

*Declaring unsupported permissions (e.g. `ROOT`, `READ_SMS`, `DEVICE_ADMIN`) will cause the package validator to reject the package immediately.*

---

## 7. Supported Runtimes

| Runtime Identifier | Architecture | Description |
| :--- | :--- | :--- |
| **`native_art`** | Native Android Runtime | Pure Kotlin/Java bytecode executed directly on ART via Kotlin Coroutines. Integrates natively with Android Foreground Services, low memory management, and network callbacks. |

---

## 8. Package Archive Format (`.botpkg`)

A `.botpkg` is a standard ZIP archive compressed using DEFLATE:
1. The archive root contains `manifest.json`, `entrypoint`, `dependencies/`, and `assets/`.
2. Total package size is optimized (typically < 100 KB for native ART bots, max 50 MB).
3. The package is hashed using `SHA-256`. The hash is stored in Cloudflare D1 for on-device download verification.

---

## 9. Platform Tooling

TapBot provides two official command-line tools in `/tools/`:

### 1. `validate-bot` (`/tools/validate-bot`)
Validates a package directory or `.botpkg` file against this specification.
```bash
node tools/validate-bot/dist/index.js ./sample-bots/music-controller-bot
```
**Verification Checks**:
- `manifest.json` exists and parses cleanly.
- All required fields are present and conform to type constraints.
- Semver string is strictly valid.
- Runtime is in the supported runtimes whitelist (`native_art`).
- Permissions are in the enforceable permissions whitelist.
- Referenced `entrypoint` and `icon` exist.
- Credentials keys are unique and use valid input types.

### 2. `package-bot` (`/tools/package-bot`)
Bundles, validates, and archives a bot directory into a deployable `.botpkg`:
```bash
node tools/package-bot/dist/index.js ./sample-bots/music-controller-bot ./dist
```
**Outputs**:
- `music-controller-bot-1.0.0.botpkg`: The signed ZIP package archive.
- `package-info.json`: Ready-to-use metadata payload containing file size, SHA-256 hash, and D1 insert commands.
