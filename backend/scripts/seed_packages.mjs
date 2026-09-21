/**
 * Script to generate valid .botpkg archives and seed them into local R2 storage & D1 database.
 * Run against the local wrangler dev server (http://127.0.0.1:8787).
 */

import * as zlib from 'zlib';
import * as crypto from 'crypto';

const BASE_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8787';
const ADMIN_KEY = process.env.ADMIN_KEY || 'dev-admin-secret-key-change-in-prod';

function createZipArchive(entries) {
    const localHeaders = [];
    const centralDirectoryHeaders = [];
    let offset = 0;

    for (const entry of entries) {
        const nameBuffer = Buffer.from(entry.name.replace(/\\/g, '/'), 'utf8');
        const uncompressedData = entry.data;
        const uncompressedSize = uncompressedData.length;
        const crc = zlib.crc32(uncompressedData);

        const compressedData = zlib.deflateRawSync(uncompressedData);
        const compressedSize = compressedData.length;

        const dosTime = (12 << 11) | (0 << 5) | 0;
        const dosDate = ((2026 - 1980) << 9) | (9 << 5) | 19;

        const localHeader = Buffer.alloc(30);
        localHeader.writeUInt32LE(0x04034b50, 0);
        localHeader.writeUInt16LE(20, 4);
        localHeader.writeUInt16LE(0, 6);
        localHeader.writeUInt16LE(8, 8);
        localHeader.writeUInt16LE(dosTime, 10);
        localHeader.writeUInt16LE(dosDate, 12);
        localHeader.writeUInt32LE(crc, 14);
        localHeader.writeUInt32LE(compressedSize, 18);
        localHeader.writeUInt32LE(uncompressedSize, 22);
        localHeader.writeUInt16LE(nameBuffer.length, 26);
        localHeader.writeUInt16LE(0, 28);

        localHeaders.push(localHeader, nameBuffer, compressedData);

        const cdHeader = Buffer.alloc(46);
        cdHeader.writeUInt32LE(0x02014b50, 0);
        cdHeader.writeUInt16LE(20, 4);
        cdHeader.writeUInt16LE(20, 6);
        cdHeader.writeUInt16LE(0, 8);
        cdHeader.writeUInt16LE(8, 10);
        cdHeader.writeUInt16LE(dosTime, 12);
        cdHeader.writeUInt16LE(dosDate, 14);
        cdHeader.writeUInt32LE(crc, 16);
        cdHeader.writeUInt32LE(compressedSize, 20);
        cdHeader.writeUInt32LE(uncompressedSize, 24);
        cdHeader.writeUInt16LE(nameBuffer.length, 28);
        cdHeader.writeUInt16LE(0, 30);
        cdHeader.writeUInt16LE(0, 32);
        cdHeader.writeUInt16LE(0, 34);
        cdHeader.writeUInt16LE(0, 36);
        cdHeader.writeUInt32LE(0, 38);
        cdHeader.writeUInt32LE(offset, 42);

        centralDirectoryHeaders.push(cdHeader, nameBuffer);
        offset += localHeader.length + nameBuffer.length + compressedData.length;
    }

    const cdStartOffset = offset;
    const cdBuffer = Buffer.concat(centralDirectoryHeaders);
    const cdSize = cdBuffer.length;

    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);
    eocd.writeUInt16LE(0, 4);
    eocd.writeUInt16LE(0, 6);
    eocd.writeUInt16LE(entries.length, 8);
    eocd.writeUInt16LE(entries.length, 10);
    eocd.writeUInt32LE(cdSize, 12);
    eocd.writeUInt32LE(cdStartOffset, 16);
    eocd.writeUInt16LE(0, 20);

    return Buffer.concat([...localHeaders, cdBuffer, eocd]);
}

const BOTS_TO_SEED = [
    {
        packageKey: 'packages/bot_rss_1.2.0.botpkg',
        manifest: {
            id: 'bot_rss',
            name: 'RSS Channel Broadcaster',
            version: '1.2.0',
            category: 'media',
            runtime: 'native_art',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            entrypoint: 'com.tapbot.bots.rss.RssBot',
            permissions: ['INTERNET', 'FOREGROUND_SERVICE'],
            credentials: [
                { key: 'bot_token', displayName: 'Telegram Bot Token', required: true, secret: true, inputType: 'password' },
                { key: 'target_chat_id', displayName: 'Target Channel / Chat ID', required: true, secret: false, inputType: 'text' },
                { key: 'feed_url', displayName: 'RSS / Atom Feed URL', required: true, secret: false, inputType: 'url' }
            ]
        }
    },
    {
        packageKey: 'packages/bot_ping_pong_1.0.0.botpkg',
        manifest: {
            id: 'bot_ping_pong',
            name: 'Ping Pong Runner',
            version: '1.0.0',
            category: 'utilities',
            runtime: 'native_art',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            entrypoint: 'com.tapbot.bots.pingpong.PingPongBot',
            permissions: ['INTERNET', 'FOREGROUND_SERVICE'],
            credentials: [
                { key: 'bot_token', displayName: 'Telegram Bot Token', required: true, secret: true, inputType: 'password' }
            ]
        }
    },
    {
        packageKey: 'packages/bot_echo_1.1.0.botpkg',
        manifest: {
            id: 'bot_echo',
            name: 'Echo Assistant Bot',
            version: '1.1.0',
            category: 'utilities',
            runtime: 'native_art',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            entrypoint: 'com.tapbot.bots.echo.EchoBot',
            permissions: ['INTERNET', 'FOREGROUND_SERVICE'],
            credentials: [
                { key: 'bot_token', displayName: 'Telegram Bot Token', required: true, secret: true, inputType: 'password' }
            ]
        }
    },
    {
        packageKey: 'packages/bot_gemini_2.0.0.botpkg',
        manifest: {
            id: 'bot_gemini',
            name: 'Gemini Smart Assistant',
            version: '2.0.0',
            category: 'productivity',
            runtime: 'native_art',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            entrypoint: 'com.tapbot.bots.gemini.GeminiBot',
            permissions: ['INTERNET', 'FOREGROUND_SERVICE'],
            credentials: [
                { key: 'bot_token', displayName: 'Telegram Bot Token', required: true, secret: true, inputType: 'password' },
                { key: 'gemini_api_key', displayName: 'Google Gemini API Key', required: true, secret: true, inputType: 'password' }
            ]
        }
    },
    {
        packageKey: 'packages/bot_draft_0.1.0.botpkg',
        manifest: {
            id: 'bot_draft_sample',
            name: 'Experimental Beta Bot',
            version: '0.1.0',
            category: 'automation',
            runtime: 'native_art',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            entrypoint: 'com.tapbot.bots.draft.DraftBot'
        }
    }
];

async function seedPackages() {
    console.log(`Starting package generation & seeding against ${BASE_URL}...\n`);

    for (const bot of BOTS_TO_SEED) {
        const manifestStr = JSON.stringify(bot.manifest, null, 2);
        const manifestBuffer = Buffer.from(manifestStr, 'utf8');

        // Create zip archive with manifest.json
        const zipBuffer = createZipArchive([
            { name: 'manifest.json', data: manifestBuffer },
            { name: 'bot/entrypoint.txt', data: Buffer.from(`Entrypoint: ${bot.manifest.entrypoint}\nCreated for TapBot Native ART`, 'utf8') }
        ]);

        const sha256 = crypto.createHash('sha256').update(zipBuffer).digest('hex');
        console.log(`[SEED] Built ${bot.packageKey}:`);
        console.log(`       Size:   ${zipBuffer.length} bytes`);
        console.log(`       SHA256: ${sha256}`);

        // Upload to backend
        const uploadUrl = `${BASE_URL}/api/v1/admin/packages/${encodeURIComponent(bot.packageKey)}`;
        const res = await fetch(uploadUrl, {
            method: 'PUT',
            headers: {
                'Authorization': `Bearer ${ADMIN_KEY}`,
                'Content-Type': 'application/octet-stream'
            },
            body: zipBuffer
        });

        if (!res.ok) {
            const errText = await res.text();
            throw new Error(`Failed to upload ${bot.packageKey}: HTTP ${res.status} - ${errText}`);
        }

        const data = await res.json();
        console.log(`       Upload response:`, data);
        console.log(`       ✓ Uploaded and DB synced successfully!\n`);
    }

    console.log('All packages seeded and synchronized with D1 & R2 successfully.');
}

seedPackages().catch((err) => {
    console.error('Fatal seeding error:', err);
    process.exit(1);
});
