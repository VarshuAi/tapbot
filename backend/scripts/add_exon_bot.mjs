import * as zlib from 'zlib';
import * as crypto from 'crypto';

const BASE_URL = process.env.BACKEND_URL || 'http://127.0.0.1:8787';
const ADMIN_KEY = process.env.ADMIN_KEY || 'dev-admin-secret-key-change-in-prod';

function createZipArchive(entries) {
    const localHeaders = [];
    const cdHeaders = [];
    let offset = 0;

    for (const entry of entries) {
        const nameBuf = Buffer.from(entry.name.replace(/\\/g, '/'), 'utf8');
        const uncompressed = entry.data;
        const crc = zlib.crc32(uncompressed);
        const compressed = zlib.deflateRawSync(uncompressed);
        const dosTime = (12 << 11) | (0 << 5) | 0;
        const dosDate = ((2026 - 1980) << 9) | (9 << 5) | 19;

        const lh = Buffer.alloc(30);
        lh.writeUInt32LE(0x04034b50, 0);
        lh.writeUInt16LE(20, 4);
        lh.writeUInt16LE(0, 6);
        lh.writeUInt16LE(8, 8);
        lh.writeUInt16LE(dosTime, 10);
        lh.writeUInt16LE(dosDate, 12);
        lh.writeUInt32LE(crc, 14);
        lh.writeUInt32LE(compressed.length, 18);
        lh.writeUInt32LE(uncompressed.length, 22);
        lh.writeUInt16LE(nameBuf.length, 26);
        lh.writeUInt16LE(0, 28);
        localHeaders.push(lh, nameBuf, compressed);

        const cd = Buffer.alloc(46);
        cd.writeUInt32LE(0x02014b50, 0);
        cd.writeUInt16LE(20, 4);
        cd.writeUInt16LE(20, 6);
        cd.writeUInt16LE(0, 8);
        cd.writeUInt16LE(8, 10);
        cd.writeUInt16LE(dosTime, 12);
        cd.writeUInt16LE(dosDate, 14);
        cd.writeUInt32LE(crc, 16);
        cd.writeUInt32LE(compressed.length, 20);
        cd.writeUInt32LE(uncompressed.length, 24);
        cd.writeUInt16LE(nameBuf.length, 28);
        cd.writeUInt16LE(0, 30);
        cd.writeUInt16LE(0, 32);
        cd.writeUInt16LE(0, 34);
        cd.writeUInt16LE(0, 36);
        cd.writeUInt32LE(0, 38);
        cd.writeUInt32LE(offset, 42);
        cdHeaders.push(cd, nameBuf);

        offset += 30 + nameBuf.length + compressed.length;
    }

    const cdBuf = Buffer.concat(cdHeaders);
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);
    eocd.writeUInt16LE(0, 4);
    eocd.writeUInt16LE(0, 6);
    eocd.writeUInt16LE(entries.length, 8);
    eocd.writeUInt16LE(entries.length, 10);
    eocd.writeUInt32LE(cdBuf.length, 12);
    eocd.writeUInt32LE(offset, 16);
    eocd.writeUInt16LE(0, 20);

    return Buffer.concat([...localHeaders, cdBuf, eocd]);
}

async function addExonBot() {
    console.log(`Connecting to admin API at ${BASE_URL}...`);

    // 1. Get bot by slug 'exon-robot'
    const listRes = await fetch(`${BASE_URL}/api/v1/admin/bots`, {
        headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
    });
    const listData = await listRes.json();
    let exon = listData.data.find(b => b.slug === 'exon-robot');

    if (!exon) {
        const botPayload = {
            name: 'Exon Robot',
            slug: 'exon-robot',
            description: 'Fast Telegram group management bot with MTProto and Bot API support.',
            longDescription: 'A modular Telegram group management bot ported from python-telegram-bot, Pyrogram, and Telethon to TapBot. Supports ban, mute, kick, anti-spam, broadcast, and group administration.',
            iconUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=200',
            category: 'automation',
            runtime: 'native_art',
            status: 'published'
        };
        const createRes = await fetch(`${BASE_URL}/api/v1/admin/bots`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${ADMIN_KEY}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(botPayload)
        });
        const created = await createRes.json();
        exon = created.data;
    }

    console.log(`Exon bot resolved: id=${exon.id}, slug=${exon.slug}`);

    // 2. Add credentials
    const credentials = [
        {
            key: 'bot_token',
            displayName: 'Telegram Bot Token',
            description: 'Token obtained from @BotFather in Telegram.',
            required: true,
            secret: true,
            inputType: 'password'
        },
        {
            key: 'api_id',
            displayName: 'App api_id',
            description: 'App API ID obtained from my.telegram.org (under API development tools).',
            required: true,
            secret: false,
            inputType: 'text'
        },
        {
            key: 'api_hash',
            displayName: 'App api_hash',
            description: 'App API Hash (32-character hexadecimal) obtained from my.telegram.org.',
            required: true,
            secret: true,
            inputType: 'password'
        },
        {
            key: 'owner_id',
            displayName: 'Owner Telegram ID',
            description: 'Your numeric Telegram User ID for admin authorization.',
            required: true,
            secret: false,
            inputType: 'text'
        }
    ];

    for (const cred of credentials) {
        await fetch(`${BASE_URL}/api/v1/admin/bots/${exon.id}/credentials`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${ADMIN_KEY}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(cred)
        });
    }
    console.log('✓ Credentials configured.');

    // 3. Generate .botpkg archive and upload to R2
    const packageKey = `packages/bot_exon_1.0.0.botpkg`;
    const manifest = {
        id: exon.id,
        name: 'Exon Robot',
        version: '1.0.0',
        category: 'automation',
        runtime: 'native_art',
        minimumAppVersion: 1,
        minimumRuntimeVersion: '1.0.0',
        entrypoint: 'com.tapbot.bots.exon.ExonBot',
        permissions: ['INTERNET', 'FOREGROUND_SERVICE', 'NOTIFICATIONS'],
        credentials: credentials
    };

    const manifestBuf = Buffer.from(JSON.stringify(manifest, null, 2), 'utf8');
    const zip = createZipArchive([
        { name: 'manifest.json', data: manifestBuf },
        { name: 'bot/exon_init.txt', data: Buffer.from('ExonRobot Port for TapBot Android Runtime\nGroup management, MTProto, and admin controls.', 'utf8') }
    ]);

    const sha256 = crypto.createHash('sha256').update(zip).digest('hex');
    console.log(`Built Exon package: size=${zip.length} bytes, sha256=${sha256}`);

    // Upload to R2
    await fetch(`${BASE_URL}/api/v1/admin/packages/${encodeURIComponent(packageKey)}`, {
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${ADMIN_KEY}`,
            'Content-Type': 'application/octet-stream'
        },
        body: zip
    });
    console.log('✓ Package uploaded to R2.');

    // 4. Create or update Version in D1
    const versionRes = await fetch(`${BASE_URL}/api/v1/admin/bots/${exon.id}/versions`, {
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${ADMIN_KEY}`,
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            version: '1.0.0',
            packageKey: packageKey,
            packageSize: zip.length,
            sha256: sha256,
            releaseNotes: 'Initial release of Exon Robot for TapBot on-device runner with MTProto & Bot API support.',
            minimumAppVersion: 1,
            minimumRuntimeVersion: '1.0.0',
            status: 'published'
        })
    });

    const verData = await versionRes.json();
    console.log('Version create response:', verData);

    // 5. Ensure bot is published
    await fetch(`${BASE_URL}/api/v1/admin/bots/${exon.id}/publish`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
    });

    // Verify public query
    const publicRes = await fetch(`${BASE_URL}/api/v1/bots/${exon.id}`);
    const publicBot = await publicRes.json();
    console.log('\nPublic Catalog Check:');
    console.log('Bot Name:', publicBot.data?.name);
    console.log('Bot Version:', publicBot.data?.version);
    console.log('Required Credentials:', publicBot.data?.requiredCredentials?.map(c => `${c.label} (${c.key})`));
    console.log('Package Size:', publicBot.data?.packageInfo?.packageSizeBytes);
    console.log('SHA256:', publicBot.data?.packageInfo?.sha256Checksum);
    console.log('\n✓ Exon Robot successfully published and ready to install in TapBot!');
}

addExonBot().catch(err => {
    console.error('Error adding Exon Bot:', err);
    process.exit(1);
});
