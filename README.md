# 🤖 TapBot: Decentralized On-Device Telegram Bot Runner & Bot Store

[![Android](https://img.shields.io/badge/Android-10%2B%20(API%2029--35)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Cloudflare Workers](https://img.shields.io/badge/Cloudflare-Workers%20%7C%20D1%20%7C%20R2-F38020?style=for-the-badge&logo=cloudflare&logoColor=white)](https://workers.cloudflare.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

> **Run real Telegram bots directly on your Android phone 24/7 without cloud servers, VPS fees, or Docker containers.** Complete data sovereignty, hardware-backed Keystore security, atomic version rollbacks, and an edge-powered decentralized Bot Store.

---

## ⚡ Overview

Traditional Telegram bot hosting requires persistent cloud servers (AWS, DigitalOcean, VPS) or complex serverless webhook relays. **TapBot** turns any modern Android smartphone into a high-efficiency, 24/7 autonomous bot host.

Bots execute locally within a sandboxed native Android ART runtime, maintaining battery-optimized HTTPS long-polling connections with the Telegram Bot API. User credentials (API tokens, private chat IDs) never leave the device and are protected by hardware-backed encryption.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                   TELEGRAM BOT API                     │
                  └──────────────────────────┬─────────────────────────────┘
                                             │ HTTPS Long-Polling (Port 443)
                                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                             ANDROID DEVICE                               │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                    TapBot Native Android Host                      │  │
│  │                                                                    │  │
│  │   ┌────────────────────────┐         ┌──────────────────────────┐  │  │
│  │   │   Foreground Service   │◄───────►│  BotInstanceManager     │  │  │
│  │   │   & WakeLock Manager   │         │  - Multi-Bot Concurrency │  │  │
│  │   └───────────┬────────────┘         │  - Atomic Rollbacks      │  │  │
│  │               │                      └────────────┬─────────────┘  │  │
│  │               ▼                                   ▼                │  │
│  │   ┌────────────────────────┐         ┌──────────────────────────┐  │  │
│  │   │ Android ART Execution  │         │ Android Keystore         │  │  │
│  │   │ Engine (Kotlin Coroutines)       │ AES-256-GCM Encryption   │  │  │
│  │   │ • Ping-Pong Bot        │         │ • Zero-Leak Redaction    │  │  │
│  │   │ • Echo Assistant Bot   │         │ • Encrypted Tokens       │  │  │
│  │   │ • RSS Broadcaster Bot  │         └──────────────────────────┘  │  │
│  │   └────────────────────────┘                                       │  │
│  └─────────────────────────────────▲──────────────────────────────────┘  │
│                                    │ Download .botpkg & Verify SHA-256   │
└────────────────────────────────────┼─────────────────────────────────────┘
                                     │
                  ┌──────────────────┴─────────────────────┐
                  │    CLOUDFLARE EDGE BACKEND (Serverless) │
                  │                                        │
                  │  • Workers API: /api/v1/bots           │
                  │  • D1 Database: Relational Catalog     │
                  │  • R2 Bucket: .botpkg Package Storage  │
                  │  • Web Admin Dashboard: /admin         │
                  └────────────────────────────────────────┘
```

---

## 🌟 Key Features

### 1. Native ART Coroutine Runtime
- **Ultra-Low Memory Footprint**: Operates in ~8 MB – 12 MB RAM per bot instance (compared to 60MB+ for Python or 90MB+ for Node.js).
- **Sub-50ms Cold Starts**: Instant startup without container initialization delays.
- **Negligible CPU Idle**: < 0.1% CPU consumption during active long-polling.
- **NAT & Firewall Piercing**: Uses HTTPS long-polling (`getUpdates`) outbound on port 443; zero port-forwarding or public IP required.
- **SELinux & Android 10+ Compliance**: 100% compliant with Android `W^X` security restrictions by executing within native ART processes without spawning arbitrary subprocesses.

### 2. Enterprise Hardware-Backed Security
- **Android Keystore Encryption**: Master keys generated inside device hardware security modules (TEE/StrongBox) with `AES-256-GCM`.
- **Zero-Leak Secret Redaction**: All logs, crash traces, and diagnostics pass through regex-powered redactors masking bot tokens (`1234****:[REDACTED_TELEGRAM_TOKEN]`).
- **No Analytics Telemetry**: Zero spyware, zero tracking SDKs, no credential forwarding.

### 3. Production Version Management & Atomic Rollback
Updates are executed with state-machine precision:
```
CHECKING ──► DOWNLOADING ──► VERIFYING ──► INSTALLING ──► STARTING ──► SUCCESS
                                                             │
                                                    (Startup Failure)
                                                             │
                                                             ▼
                                                       ROLLING_BACK ──► RESTORED OLD VERSION
```
- **Integrity Validation**: Computes on-the-fly SHA-256 verification and verifies `manifest.json` compatibility before touching existing installations.
- **Anti-Downgrade Protection**: Rejects downgrade attacks trying to force older vulnerable versions.
- **Preserved State**: Credential tokens and custom settings are preserved during updates and rollbacks.
- **Guaranteed Fallback**: Previous `.botpkg` archive is retained until health checks confirm the new version is responding.

### 4. Edge Bot Store & Web Admin Portal
- **Cloudflare Edge Architecture**: Fast global delivery using Cloudflare Workers, Cloudflare D1 (Serverless SQLite), and Cloudflare R2 object storage.
- **Web-Based Admin Dashboard (`/admin`)**: Single-Page App with Dark Ops HUD aesthetic for publishers to manage bots, upload `.botpkg` archives, inspect checksums, and publish/unpublish versions.
- **Zero Frontend Secrets**: All administrative endpoints require `Bearer` token authorization verified at the Cloudflare edge.
- **Bot Lifecycle State Machine**: Full lifecycle tracking: `DRAFT` ➔ `REVIEW` ➔ `PUBLISHED` ➔ `UNPUBLISHED` ➔ `ARCHIVED`.
- **Tamper-Evident Audit Logging**: Every publication, unpublish, credential change, and package upload is logged to an immutable SQLite audit table.

---

## 📊 Performance Benchmarks

Measured on physical Android hardware and BlueStacks virtualization:

| Workload / State | CPU Usage | RAM Usage | Network Overhead | Battery Drain / hr |
| :--- | :--- | :--- | :--- | :--- |
| **1 Bot Idle** | < 0.1% | 8.2 MB | ~1.1 KB / poll | ~0.15% |
| **1 Bot Active** (10 msgs/min) | 0.4% – 0.9% | 11.4 MB | ~4.5 KB / msg | ~0.35% |
| **3 Bots Idle** | < 0.2% | 19.8 MB | ~3.3 KB / poll | ~0.40% |
| **3 Bots Active** (concurrent) | 1.2% – 2.1% | 26.5 MB | ~14.0 KB / msg | ~0.85% |
| **Deep Doze Mode** | 0.0% | 7.9 MB | Maintenance windows only | < 0.05% |

> Detailed benchmarks, profiling scripts, and Doze mode analysis are available in [`PERFORMANCE.md`](PERFORMANCE.md).

---

## 🗂 Project Architecture

```
tapbot/
├── app/                           # Android Application Shell
│   ├── src/main/java/com/tapbot/  # TapBotApplication, NavHost, DI wiring
│   └── src/main/res/              # Drawables, layouts, theme configurations
├── core/                          # Reusable Core Domain & Infrastructure
│   ├── model/                     # Pure domain models (BotInstance, BotMetadata, BotVersion)
│   ├── network/                   # OkHttp long-polling, Cloudflare Catalog API, Verifiers
│   ├── runner/                    # ART Coroutine Execution Engine, BotInstanceManager, Service
│   ├── security/                  # KeystoreCredentialStore, SecretRedactor, SHA-256 verifiers
│   └── logging/                   # Redacted circular log buffer, structured event loggers
├── feature/                       # Jetpack Compose UI Feature Modules
│   ├── catalog/                   # Bot Store catalog, category filters, search
│   ├── botdetail/                 # Bot details, dynamic credential inputs, installation flow
│   ├── runner/                    # Live dashboard, active instances, runtime logs console
│   └── settings/                  # Battery saver whitelist, Keystore settings, diagnostics
├── backend/                       # Cloudflare Edge Backend
│   ├── src/index.ts               # Cloudflare Worker router & security middleware
│   ├── src/routes/public.ts       # Public Bot Store API (/api/v1/bots, /api/v1/packages)
│   ├── src/routes/admin.ts        # Secure Admin API (/api/v1/admin/*)
│   ├── src/dashboard/html.ts      # Cyberpunk Dark Ops Web Admin Dashboard SPA
│   ├── migrations/                # D1 SQLite schema & migration scripts
│   └── test/                      # Vitest test suite for edge APIs
├── tools/                         # Developer CLI Utilities
│   ├── package-bot/               # CLI packager compiling bots into .botpkg archives
│   └── validate-bot/              # CLI validator checking manifest.json syntax
├── docs/                          # Architecture Specifications
│   └── BOT_SPEC.md                # .botpkg packaging specification & manifest schema
├── PERFORMANCE.md                 # Dedicated performance engineering & measurement report
├── SECURITY.md                    # Threat model, Keystore design & vulnerability policy
└── PHASE_2_RUNTIME.md             # Technical proof-of-concept evaluation & trade-offs
```

---

## 📦 The `.botpkg` Package Specification

A `.botpkg` is a cryptographically verified ZIP archive containing the bot's runtime code, assets, and descriptor:

```
my-bot-1.0.0.botpkg
├── manifest.json                  # Required: Bot descriptor and metadata
├── icon.png                       # Optional: Local icon asset
└── bot/                           # Bot executable payload
    └── (classes.dex or scripts)
```

### `manifest.json` Example
```json
{
  "id": "rss-channel-broadcaster",
  "name": "RSS Channel Broadcaster",
  "version": "1.2.0",
  "category": "media",
  "runtime": "native_art",
  "minimumAppVersion": 1,
  "minimumRuntimeVersion": "1.0.0",
  "entrypoint": "com.tapbot.bots.rss.RssBot",
  "permissions": [
    "INTERNET",
    "FOREGROUND_SERVICE"
  ],
  "credentials": [
    {
      "key": "bot_token",
      "displayName": "Telegram Bot Token",
      "required": true,
      "secret": true,
      "inputType": "password"
    },
    {
      "key": "target_chat_id",
      "displayName": "Target Channel / Chat ID",
      "required": true,
      "secret": false,
      "inputType": "text"
    }
  ]
}
```

---

## 🚀 Quick Start Guide

### Prerequisites
- **Android Studio Jellyfish | Ladybug (2024.1+)** or Android SDK command-line tools.
- **JDK 17 or JDK 21**.
- **Node.js 18+ & npm** (for Cloudflare backend and packaging tools).
- An Android Device (Android 10+, API 29+) or Emulator / BlueStacks.

---

### 1. Build and Run the Android App

```powershell
# Clone the repository
git clone https://github.com/VarshuAi/tapbot.git
cd tapbot

# Build debug APK
.\gradlew.bat assembleDebug

# Run unit tests across all modules
.\gradlew.bat testDebugUnitTest

# Install to connected device or emulator via adb
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

---

### 2. Run the Cloudflare Workers Backend Locally

```powershell
cd backend

# Install dependencies
npm install

# Run D1 migrations locally in Miniflare
npx wrangler d1 migrations apply tapbot-catalog --local

# Start Wrangler dev server (listening on all interfaces for emulator access)
npx wrangler dev --ip 0.0.0.0 --port 8787
```

- **Public API**: `http://localhost:8787/api/v1/bots`
- **Admin Dashboard**: Open `http://localhost:8787/admin` in your browser.
  - Default Local Admin Key: `dev-admin-secret-key-change-in-prod`

---

### 3. Package a Custom Bot

```powershell
cd tools/package-bot

# Build packager CLI
npm install && npm run build

# Package your bot folder into a .botpkg
node dist/index.js --source path/to/my-bot --output dist/
```

---

## 🔒 Security & Privacy

TapBot adheres to a strict Zero-Trust client architecture:
1. **Device-Bounded Secrets**: Telegram API credentials are encrypted with hardware-backed Android Keystore keys and decrypted only in RAM when making HTTPS calls.
2. **Zero Plaintext Logs**: Automatic sanitization filters tokens from Android Logcat and app diagnostics.
3. **Download Verification**: Packages are verified against remote SHA-256 checksums before unpacking.
4. **Isolated Memory**: Each bot instance executes within its own coroutine scope and memory boundary.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:
1. Fork the project.
2. Create your feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

---

## 📄 License

Distributed under the Apache License, Version 2.0. See [`LICENSE`](LICENSE) for more details.

Copyright © 2026 TapBot Authors.
