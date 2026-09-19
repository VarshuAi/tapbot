import { describe, it, expect, beforeEach } from 'vitest';
import worker, { Env } from '../src/index';
import { BotRow, BotVersionRow, BotCredentialRow, CategoryRow } from '../src/types';

// In-Memory SQLite/D1 Mock for Vitest
class MockD1 {
    categories: CategoryRow[] = [
        { id: 'cat_utilities', name: 'Utilities', slug: 'utilities' },
        { id: 'cat_productivity', name: 'Productivity', slug: 'productivity' }
    ];

    bots: BotRow[] = [
        {
            id: 'bot_ping_pong',
            slug: 'ping-pong-bot',
            name: 'Ping Pong Runner',
            description: 'Ultra-lightweight Telegram bot testing on-device latency.',
            long_description: 'Full description of ping pong runner.',
            icon_url: 'https://example.com/icon.png',
            category: 'utilities',
            runtime: 'native_art',
            current_version: '1.0.0',
            status: 'published',
            created_at: '2026-09-19T00:00:00Z',
            updated_at: '2026-09-19T00:00:00Z'
        },
        {
            id: 'bot_draft_sample',
            slug: 'draft-sample',
            name: 'Draft Bot',
            description: 'Not yet published.',
            long_description: null,
            icon_url: null,
            category: 'utilities',
            runtime: 'native_art',
            current_version: '0.1.0',
            status: 'draft',
            created_at: '2026-09-19T00:00:00Z',
            updated_at: '2026-09-19T00:00:00Z'
        }
    ];

    versions: BotVersionRow[] = [
        {
            id: 'ver_pp_100',
            bot_id: 'bot_ping_pong',
            version: '1.0.0',
            package_key: 'packages/bot_ping_pong_1.0.0.botpkg',
            package_size: 15420,
            sha256: '7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069',
            release_notes: 'Initial release',
            minimum_app_version: 1,
            published_at: '2026-09-19T00:00:00Z'
        }
    ];

    credentials: BotCredentialRow[] = [
        {
            id: 'cred_pp_token',
            bot_id: 'bot_ping_pong',
            key: 'bot_token',
            display_name: 'Telegram Bot Token',
            description: 'From @BotFather',
            required: 1,
            secret: 1,
            input_type: 'password'
        }
    ];

    prepare(query: string) {
        return new MockPreparedStatement(this, query);
    }

    async batch(statements: MockPreparedStatement[]) {
        for (const stmt of statements) {
            await stmt.run();
        }
        return [];
    }
}

class MockPreparedStatement {
    constructor(private db: MockD1, private query: string, private params: any[] = []) {}

    bind(...params: any[]) {
        return new MockPreparedStatement(this.db, this.query, params);
    }

    async all<T = any>(): Promise<{ results: T[] }> {
        const q = this.query.trim().toLowerCase();

        if (q.includes('select id, name, slug from categories')) {
            return { results: [...this.db.categories] as any };
        }

        if (q.includes('from bots where status = \'published\'')) {
            let res = this.db.bots.filter(b => b.status === 'published');
            if (q.includes('and category = ?') && this.params.length > 0) {
                res = res.filter(b => b.category === this.params[0]);
            }
            return { results: res as any };
        }

        if (q.includes('from bot_credentials where bot_id = ?')) {
            const botId = this.params[0];
            const creds = this.db.credentials.filter(c => c.bot_id === botId);
            return { results: creds as any };
        }

        if (q.includes('from bot_versions where bot_id = ?')) {
            const botId = this.params[0];
            const vers = this.db.versions.filter(v => v.bot_id === botId);
            return { results: vers as any };
        }

        return { results: [] };
    }

    async first<T = any>(): Promise<T | null> {
        const q = this.query.trim().toLowerCase();

        if (q.includes('from bots where (id = ? or slug = ?) and status = \'published\'')) {
            const idOrSlug = this.params[0];
            const found = this.db.bots.find(b => (b.id === idOrSlug || b.slug === idOrSlug) && b.status === 'published');
            return (found || null) as any;
        }

        if (q.includes('from bots where id = ? or slug = ?')) {
            const idOrSlug = this.params[0];
            const found = this.db.bots.find(b => b.id === idOrSlug || b.slug === idOrSlug);
            return (found || null) as any;
        }

        if (q.includes('from bots where slug = ?')) {
            const slug = this.params[0];
            const found = this.db.bots.find(b => b.slug === slug);
            return (found || null) as any;
        }

        if (q.includes('from bots where id = ?')) {
            const id = this.params[0];
            const found = this.db.bots.find(b => b.id === id);
            return (found || null) as any;
        }

        if (q.includes('from bot_versions where id = ?')) {
            const id = this.params[0];
            const found = this.db.versions.find(v => v.id === id);
            return (found || null) as any;
        }

        if (q.includes('from bot_versions where bot_id = ? and version = ?')) {
            const [botId, version] = this.params;
            const found = this.db.versions.find(v => v.bot_id === botId && v.version === version);
            return (found || null) as any;
        }

        return null;
    }

    async run(): Promise<{ success: boolean }> {
        const q = this.query.trim().toLowerCase();

        if (q.includes('insert into bots')) {
            const [id, slug, name, desc, longDesc, icon, cat, runtime, created, updated] = this.params;
            this.db.bots.push({
                id,
                slug,
                name,
                description: desc,
                long_description: longDesc,
                icon_url: icon,
                category: cat,
                runtime,
                current_version: null,
                status: 'draft',
                created_at: created,
                updated_at: updated
            });
            return { success: true };
        }

        if (q.includes('update bots set status = \'published\'')) {
            const [updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = 'published';
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes('update bots set status = \'draft\'')) {
            const [updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = 'draft';
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes('insert into bot_versions')) {
            const [id, botId, version, pkgKey, pkgSize, sha, notes, minApp, pubAt] = this.params;
            this.db.versions.push({
                id,
                bot_id: botId,
                version,
                package_key: pkgKey,
                package_size: pkgSize,
                sha256: sha,
                release_notes: notes,
                minimum_app_version: minApp,
                published_at: pubAt
            });
            return { success: true };
        }

        if (q.includes('update bots set current_version = ?')) {
            const [version, updatedAt, botId] = this.params;
            const b = this.db.bots.find(x => x.id === botId);
            if (b) {
                b.current_version = version;
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes('delete from bots where id = ?')) {
            const [id] = this.params;
            this.db.bots = this.db.bots.filter(x => x.id !== id);
            return { success: true };
        }

        if (q.includes('delete from bot_versions where bot_id = ?')) {
            const [botId] = this.params;
            this.db.versions = this.db.versions.filter(x => x.bot_id !== botId);
            return { success: true };
        }

        if (q.includes('delete from bot_credentials where bot_id = ?')) {
            const [botId] = this.params;
            this.db.credentials = this.db.credentials.filter(x => x.bot_id !== botId);
            return { success: true };
        }

        return { success: true };
    }
}

// In-Memory R2 Mock
class MockR2 {
    storage = new Map<string, Uint8Array>();

    async get(key: string) {
        const data = this.storage.get(key);
        if (!data) return null;

        return {
            body: data,
            httpEtag: '"test-etag-123"',
            writeHttpMetadata: (headers: Headers) => {
                headers.set('content-type', 'application/octet-stream');
            }
        };
    }

    async put(key: string, value: any) {
        let bytes: Uint8Array;
        if (value instanceof Uint8Array) {
            bytes = value;
        } else if (typeof value === 'string') {
            bytes = new TextEncoder().encode(value);
        } else if (value && typeof value.arrayBuffer === 'function') {
            bytes = new Uint8Array(await value.arrayBuffer());
        } else if (value) {
            // ReadableStream
            const buf = await new Response(value).arrayBuffer();
            bytes = new Uint8Array(buf);
        } else {
            bytes = new Uint8Array([1, 2, 3]);
        }
        this.storage.set(key, bytes);
        return {
            size: bytes.length,
            etag: '"uploaded-etag"'
        };
    }
}

describe('TapBot Catalog Backend API', () => {
    let mockDb: MockD1;
    let mockR2: MockR2;
    let env: Env;
    const ADMIN_KEY = 'test-admin-secret-key';

    beforeEach(() => {
        mockDb = new MockD1();
        mockR2 = new MockR2();
        env = {
            DB: mockDb as any,
            BUCKET: mockR2 as any,
            ADMIN_API_KEY: ADMIN_KEY,
            ENVIRONMENT: 'test'
        };
    });

    it('handles CORS OPTIONS preflight', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots', {
            method: 'OPTIONS'
        });
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(204);
        expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
        expect(res.headers.get('Access-Control-Allow-Methods')).toContain('GET');
        expect(res.headers.get('Access-Control-Allow-Methods')).toContain('POST');
    });

    it('returns categories list (GET /api/v1/categories)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/categories');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data).toHaveLength(2);
        expect(body.data[0].slug).toBe('utilities');
    });

    it('returns published bots list (GET /api/v1/bots)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data).toHaveLength(1);
        expect(body.data[0].slug).toBe('ping-pong-bot');
        expect(body.data[0].credentials).toBeDefined();
        expect(body.data[0].credentials[0].key).toBe('bot_token');
        expect(body.data[0].currentVersionInfo).toBeDefined();
        expect(body.data[0].currentVersionInfo.version).toBe('1.0.0');
    });

    it('returns single bot details by slug (GET /api/v1/bots/:id)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots/ping-pong-bot');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.name).toBe('Ping Pong Runner');
        expect(body.data.credentials).toHaveLength(1);
        expect(body.data.credentials[0].key).toBe('bot_token');
        expect(body.data.credentials[0].required).toBe(true);
        expect(body.data.credentials[0].secret).toBe(true);
    });

    it('returns 404 for non-existent or draft bot in public endpoint', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots/draft-sample');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(404);

        const body = await res.json();
        expect(body.success).toBe(false);
        expect(body.error.code).toBe('NOT_FOUND');
    });

    it('returns version history (GET /api/v1/bots/:id/versions)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots/ping-pong-bot/versions');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data).toHaveLength(1);
        expect(body.data[0].version).toBe('1.0.0');
        expect(body.data[0].packageKey).toBe('packages/bot_ping_pong_1.0.0.botpkg');
    });

    it('rejects unauthenticated requests to admin endpoints', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/bots', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                slug: 'unauthorized-bot',
                name: 'Unauthorized',
                description: 'Should fail',
                category: 'utilities'
            })
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(401);

        const body = await res.json();
        expect(body.success).toBe(false);
        expect(body.error.code).toBe('UNAUTHORIZED');
    });

    it('allows admin to create a new draft bot with valid auth', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/bots', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                slug: 'new-cool-bot',
                name: 'New Cool Bot',
                description: 'Created by admin',
                category: 'productivity'
            })
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(201);

        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.slug).toBe('new-cool-bot');
        expect(body.data.status).toBe('draft');
    });

    it('allows admin to publish and unpublish a bot', async () => {
        // Publish
        const pubReq = new Request('https://api.tapbot.internal/api/v1/admin/bots/draft-sample/publish', {
            method: 'POST',
            headers: { 'X-Admin-Key': ADMIN_KEY }
        });
        const pubRes = await worker.fetch(pubReq, env);
        expect(pubRes.status).toBe(200);
        const pubBody = await pubRes.json();
        expect(pubBody.data.status).toBe('published');

        // Unpublish
        const unpubReq = new Request('https://api.tapbot.internal/api/v1/admin/bots/draft-sample/unpublish', {
            method: 'POST',
            headers: { 'X-Admin-Key': ADMIN_KEY }
        });
        const unpubRes = await worker.fetch(unpubReq, env);
        expect(unpubRes.status).toBe(200);
        const unpubBody = await unpubRes.json();
        expect(unpubBody.data.status).toBe('draft');
    });

    it('allows admin to register a new bot version', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/bots/ping-pong-bot/versions', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                version: '1.1.0',
                packageKey: 'packages/bot_ping_pong_1.1.0.botpkg',
                packageSize: 18900,
                sha256: 'abc1234567890abcdef',
                releaseNotes: 'Performance fixes'
            })
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(201);
        const body = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.version).toBe('1.1.0');
    });

    it('allows uploading to R2 and downloading from public endpoint', async () => {
        const dummyPackage = new TextEncoder().encode('DUMMY_BINARY_ARCHIVE_DATA');

        // Admin upload to R2
        const uploadReq = new Request('https://api.tapbot.internal/api/v1/admin/packages/packages/test.botpkg', {
            method: 'PUT',
            headers: {
                'Authorization': `Bearer ${ADMIN_KEY}`,
                'Content-Type': 'application/octet-stream'
            },
            body: dummyPackage
        });
        const uploadRes = await worker.fetch(uploadReq, env);
        expect(uploadRes.status).toBe(201);

        // Public download from R2
        const downloadReq = new Request('https://api.tapbot.internal/api/v1/packages/packages/test.botpkg');
        const downloadRes = await worker.fetch(downloadReq, env);
        expect(downloadRes.status).toBe(200);
        expect(downloadRes.headers.get('Content-Disposition')).toContain('test.botpkg');

        const downloadedBytes = new Uint8Array(await downloadRes.arrayBuffer());
        expect(downloadedBytes).toEqual(dummyPackage);
    });
});
