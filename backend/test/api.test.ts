import { describe, it, expect, beforeEach } from 'vitest';
import worker, { Env } from '../src/index';
import {
    BotRow,
    BotVersionRow,
    BotCredentialRow,
    CategoryRow,
    AuditLogRow,
    BotLifecycleStatus
} from '../src/types';

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
            minimum_runtime_version: '1.0.0',
            status: 'published',
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

    auditLogs: AuditLogRow[] = [];

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

        if (q.includes("from bots where status = 'published'")) {
            let res = this.db.bots.filter(b => b.status === 'published');
            if (q.includes('and category = ?') && this.params.length > 0) {
                res = res.filter(b => b.category === this.params[0]);
            }
            return { results: res as any };
        }

        if (q.includes('select status, count(*) as count from bots group by status')) {
            const counts: Record<string, number> = {};
            for (const b of this.db.bots) {
                counts[b.status] = (counts[b.status] || 0) + 1;
            }
            const results = Object.entries(counts).map(([status, count]) => ({ status, count }));
            return { results: results as any };
        }

        if (q.startsWith('select * from bots')) {
            let res = [...this.db.bots];
            if (q.includes('where status = ?') && this.params.length > 0) {
                res = res.filter(b => b.status === this.params[0]);
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
            let vers = this.db.versions.filter(v => v.bot_id === botId);
            if (q.includes("status = 'published'")) {
                vers = vers.filter(v => v.status === 'published');
            }
            return { results: vers as any };
        }

        if (q.includes('from audit_logs')) {
            const limit = this.params.length > 0 ? this.params[0] : 50;
            const sorted = [...this.db.auditLogs].sort((a, b) => b.created_at.localeCompare(a.created_at));
            return { results: sorted.slice(0, limit) as any };
        }

        return { results: [] };
    }

    async first<T = any>(): Promise<T | null> {
        const q = this.query.trim().toLowerCase();

        if (q.includes('select count(*) as total from bots')) {
            return { total: this.db.bots.length } as any;
        }

        if (q.includes('select count(*) as total from bot_versions')) {
            return { total: this.db.versions.length } as any;
        }

        if (q.includes('select count(*) as count from bot_versions where bot_id = ?')) {
            const botId = this.params[0];
            const cnt = this.db.versions.filter(v => v.bot_id === botId).length;
            return { count: cnt } as any;
        }

        if (q.includes("from bots where (id = ? or slug = ?) and status = 'published'")) {
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

        if (q.includes("from bot_versions where bot_id = ? and status = 'published'")) {
            const botId = this.params[0];
            const found = this.db.versions
                .filter(v => v.bot_id === botId && v.status === 'published')
                .sort((a, b) => b.published_at.localeCompare(a.published_at))[0];
            return (found || null) as any;
        }

        return null;
    }

    async run(): Promise<{ success: boolean }> {
        const q = this.query.trim().toLowerCase();

        if (q.includes('insert into audit_logs')) {
            const [id, action, entityType, entityId, details, actor, createdAt] = this.params;
            this.db.auditLogs.push({
                id,
                action,
                entity_type: entityType,
                entity_id: entityId,
                details,
                actor,
                created_at: createdAt
            });
            return { success: true };
        }

        if (q.includes('insert into bots')) {
            const [id, slug, name, desc, longDesc, icon, cat, runtime, status, created, updated] = this.params;
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
                status: status || 'draft',
                created_at: created,
                updated_at: updated
            });
            return { success: true };
        }

        if (q.includes('update bots set status = ?, updated_at = ? where id = ?')) {
            const [status, updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = status;
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes("update bots set status = 'published'")) {
            const [updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = 'published';
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes("update bots set status = 'draft'")) {
            const [updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = 'draft';
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes("update bots set status = 'unpublished'")) {
            const [updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.status = 'unpublished';
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes('update bots set name = ?')) {
            const [name, desc, longDesc, icon, cat, runtime, status, updatedAt, id] = this.params;
            const b = this.db.bots.find(x => x.id === id);
            if (b) {
                b.name = name;
                b.description = desc;
                b.long_description = longDesc;
                b.icon_url = icon;
                b.category = cat;
                b.runtime = runtime;
                b.status = status;
                b.updated_at = updatedAt;
            }
            return { success: true };
        }

        if (q.includes('insert into bot_versions')) {
            const [id, botId, version, pkgKey, pkgSize, sha, notes, minApp, minRuntime, status, pubAt] = this.params;
            this.db.versions.push({
                id,
                bot_id: botId,
                version,
                package_key: pkgKey,
                package_size: pkgSize,
                sha256: sha,
                release_notes: notes,
                minimum_app_version: minApp,
                minimum_runtime_version: minRuntime || '1.0.0',
                status: status || 'published',
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

        if (q.includes('delete from bot_credentials where bot_id = ? and key = ?')) {
            const [botId, key] = this.params;
            this.db.credentials = this.db.credentials.filter(x => !(x.bot_id === botId && x.key === key));
            return { success: true };
        }

        if (q.includes('delete from bot_credentials where bot_id = ?')) {
            const [botId] = this.params;
            this.db.credentials = this.db.credentials.filter(x => x.bot_id !== botId);
            return { success: true };
        }

        if (q.includes('insert into bot_credentials')) {
            const [id, botId, key, displayName, description, required, secret, inputType] = this.params;
            this.db.credentials.push({
                id,
                bot_id: botId,
                key,
                display_name: displayName,
                description,
                required,
                secret,
                input_type: inputType
            });
            return { success: true };
        }

        return { success: true };
    }
}

// In-Memory R2 Mock
class MockR2 {
    storage = new Map<string, { bytes: Uint8Array; contentType?: string }>();

    async get(key: string) {
        const item = this.storage.get(key);
        if (!item) return null;

        return {
            body: item.bytes,
            httpEtag: '"test-etag-123"',
            httpMetadata: { contentType: item.contentType || 'application/octet-stream' },
            writeHttpMetadata: (headers: Headers) => {
                headers.set('content-type', item.contentType || 'application/octet-stream');
            }
        };
    }

    async put(key: string, value: any, options?: any) {
        let bytes: Uint8Array;
        if (value instanceof Uint8Array) {
            bytes = value;
        } else if (value instanceof ArrayBuffer) {
            bytes = new Uint8Array(value);
        } else if (typeof value === 'string') {
            bytes = new TextEncoder().encode(value);
        } else if (value && typeof value.arrayBuffer === 'function') {
            bytes = new Uint8Array(await value.arrayBuffer());
        } else if (value) {
            const buf = await new Response(value).arrayBuffer();
            bytes = new Uint8Array(buf);
        } else {
            bytes = new Uint8Array([1, 2, 3]);
        }
        const contentType = options?.httpMetadata?.contentType;
        this.storage.set(key, { bytes, contentType });
        return {
            size: bytes.length,
            etag: '"uploaded-etag"'
        };
    }
}

describe('TapBot Catalog Backend API & Admin Dashboard', () => {
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

    // -------------------------------------------------------------
    // Dashboard SPA Serving
    // -------------------------------------------------------------
    it('serves admin dashboard HTML on GET /admin with security headers', async () => {
        const req = new Request('https://api.tapbot.internal/admin');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);
        expect(res.headers.get('Content-Type')).toContain('text/html');
        expect(res.headers.get('X-Frame-Options')).toBe('DENY');
        expect(res.headers.get('X-Content-Type-Options')).toBe('nosniff');

        const html = await res.text();
        expect(html).toContain('TapBot Store Admin');
        expect(html).toContain('Not available'); // Analytics policy
        expect(html).not.toContain(ADMIN_KEY); // Zero secrets in frontend source
    });

    // -------------------------------------------------------------
    // Admin Stats & Analytics Check
    // -------------------------------------------------------------
    it('returns stats overview with strictly "Not available" analytics on GET /api/v1/admin/stats', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/stats', {
            headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
        });
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.totalBots).toBe(2);
        expect(body.data.publishedBots).toBe(1);
        expect(body.data.draftBots).toBe(1);
        expect(body.data.totalVersions).toBe(1);
        // User requirement: "Do not implement fake analytics. If install/download analytics have not been implemented, display 'Not available'"
        expect(body.data.downloads).toBe('Not available');
        expect(body.data.installs).toBe('Not available');
        expect(body.data.lifecycleBreakdown).toBeDefined();
    });

    // -------------------------------------------------------------
    // Admin Bot Management (All Lifecycle States)
    // -------------------------------------------------------------
    it('allows admin to list all bots across all lifecycle states on GET /api/v1/admin/bots', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/bots', {
            headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
        });
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data).toHaveLength(2); // ping-pong (published) & draft-sample (draft)
        const slugs = body.data.map((b: any) => b.slug);
        expect(slugs).toContain('ping-pong-bot');
        expect(slugs).toContain('draft-sample');
    });

    it('allows admin to transition lifecycle across DRAFT, REVIEW, PUBLISHED, UNPUBLISHED, ARCHIVED', async () => {
        const testStates: BotLifecycleStatus[] = ['review', 'published', 'unpublished', 'archived', 'draft'];

        for (const state of testStates) {
            const req = new Request('https://api.tapbot.internal/api/v1/admin/bots/draft-sample/lifecycle', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${ADMIN_KEY}`
                },
                body: JSON.stringify({ status: state.toUpperCase() })
            });

            const res = await worker.fetch(req, env);
            expect(res.status).toBe(200);
            const body: any = await res.json();
            expect(body.success).toBe(true);
            expect(body.data.status).toBe(state);
        }
    });

    it('rejects invalid lifecycle status with 400', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/admin/bots/draft-sample/lifecycle', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({ status: 'INVALID_STATUS' })
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(400);
        const body: any = await res.json();
        expect(body.success).toBe(false);
        expect(body.error.code).toBe('VALIDATION_ERROR');
    });

    // -------------------------------------------------------------
    // Direct Package Upload & SHA-256 Checksum Calculation
    // -------------------------------------------------------------
    it('computes Web Crypto SHA-256 and saves package to R2 on POST /api/v1/admin/upload/package', async () => {
        const packageContent = new TextEncoder().encode('SAMPLE_TELEGRAM_BOT_PACKAGE_CONTENT');

        const req = new Request('https://api.tapbot.internal/api/v1/admin/upload/package', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/octet-stream',
                'X-Filename': 'music_bot_1.0.0.botpkg',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: packageContent
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(201);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.packageKey).toContain('packages/');
        expect(body.data.packageSize).toBe(packageContent.byteLength);
        expect(body.data.sha256).toHaveLength(64); // Valid SHA-256 hex string
    });

    // -------------------------------------------------------------
    // Direct Icon Upload
    // -------------------------------------------------------------
    it('uploads icon image to R2 on POST /api/v1/admin/upload/icon', async () => {
        const iconBytes = new Uint8Array([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]); // PNG header

        const req = new Request('https://api.tapbot.internal/api/v1/admin/upload/icon', {
            method: 'POST',
            headers: {
                'Content-Type': 'image/png',
                'X-Filename': 'app_icon.png',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: iconBytes
        });

        const res = await worker.fetch(req, env);
        expect(res.status).toBe(201);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.iconKey).toContain('icons/');
        expect(body.data.iconUrl).toContain('/api/v1/packages/');
    });

    // -------------------------------------------------------------
    // Credential Configuration & Deletion
    // -------------------------------------------------------------
    it('configures and deletes bot credentials via admin API', async () => {
        // Add credential
        const addReq = new Request('https://api.tapbot.internal/api/v1/admin/bots/ping-pong-bot/credentials', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                key: 'spotify_client_id',
                displayName: 'Spotify Client ID',
                required: false,
                secret: false,
                inputType: 'text'
            })
        });
        const addRes = await worker.fetch(addReq, env);
        expect(addRes.status).toBe(201);

        // Delete credential
        const delReq = new Request('https://api.tapbot.internal/api/v1/admin/bots/ping-pong-bot/credentials/spotify_client_id', {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
        });
        const delRes = await worker.fetch(delReq, env);
        expect(delRes.status).toBe(200);
        const delBody: any = await delRes.json();
        expect(delBody.data.deleted).toBe(true);
    });

    // -------------------------------------------------------------
    // Audit Trail Verification
    // -------------------------------------------------------------
    it('persists and retrieves audit logs on GET /api/v1/admin/audit', async () => {
        // Trigger a mutating action to generate an audit log
        await worker.fetch(new Request('https://api.tapbot.internal/api/v1/admin/bots', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                name: 'Audit Target Bot',
                slug: 'audit-target-bot',
                description: 'Testing audit trail generation',
                category: 'utilities'
            })
        }), env);

        const req = new Request('https://api.tapbot.internal/api/v1/admin/audit', {
            headers: { 'Authorization': `Bearer ${ADMIN_KEY}` }
        });
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(Array.isArray(body.data)).toBe(true);
        expect(body.data.length).toBeGreaterThan(0);
        expect(body.data[0].action).toBe('create_bot');
        expect(body.data[0].actor).toBe('admin');
    });

    // -------------------------------------------------------------
    // End-to-End Flow: Create -> Upload Package -> Publish -> Available to Android
    // -------------------------------------------------------------
    it('completes end-to-end publisher flow making new bot immediately available to Android clients', async () => {
        // 1. Create bot in DRAFT
        const createReq = new Request('https://api.tapbot.internal/api/v1/admin/bots', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                name: 'E2E Music Bot',
                slug: 'e2e-music-bot',
                description: 'Telegram music bot for Android',
                category: 'media',
                runtime: 'native_art',
                status: 'draft'
            })
        });
        const createRes = await worker.fetch(createReq, env);
        expect(createRes.status).toBe(201);
        const createdBot = (await createRes.json() as any).data;

        // Verify Android client CANNOT see draft bot
        const publicCheck1 = await worker.fetch(new Request('https://api.tapbot.internal/api/v1/bots'), env);
        const publicBots1: any = await publicCheck1.json();
        expect(publicBots1.data.find((b: any) => b.slug === 'e2e-music-bot')).toBeUndefined();

        // 2. Upload Package
        const pkgUpload = await worker.fetch(new Request('https://api.tapbot.internal/api/v1/admin/upload/package', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/octet-stream',
                'X-Filename': 'e2e-music-1.0.0.botpkg',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: new TextEncoder().encode('MUSIC_BOT_BINARY_PKG')
        }), env);
        expect(pkgUpload.status).toBe(201);
        const uploadResult = (await pkgUpload.json() as any).data;

        // 3. Register Version
        const verReq = await worker.fetch(new Request(`https://api.tapbot.internal/api/v1/admin/bots/${createdBot.id}/versions`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({
                version: '1.0.0',
                packageKey: uploadResult.packageKey,
                packageSize: uploadResult.packageSize,
                sha256: uploadResult.sha256,
                releaseNotes: 'V1 initial launch'
            })
        }), env);
        expect(verReq.status).toBe(201);

        // 4. Set Lifecycle to PUBLISHED
        const pubReq = await worker.fetch(new Request(`https://api.tapbot.internal/api/v1/admin/bots/${createdBot.id}/lifecycle`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({ status: 'PUBLISHED' })
        }), env);
        expect(pubReq.status).toBe(200);

        // 5. Verify Android client IMMEDIATELY sees published bot
        const publicCheck2 = await worker.fetch(new Request('https://api.tapbot.internal/api/v1/bots'), env);
        const publicBots2: any = await publicCheck2.json();
        const foundBot = publicBots2.data.find((b: any) => b.slug === 'e2e-music-bot');
        expect(foundBot).toBeDefined();
        expect(foundBot.name).toBe('E2E Music Bot');
        expect(foundBot.currentVersion).toBe('1.0.0');

        // 6. Unpublish bot -> Immediately disappears from Android clients
        const unpubReq = await worker.fetch(new Request(`https://api.tapbot.internal/api/v1/admin/bots/${createdBot.id}/lifecycle`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${ADMIN_KEY}`
            },
            body: JSON.stringify({ status: 'UNPUBLISHED' })
        }), env);
        expect(unpubReq.status).toBe(200);

        const publicCheck3 = await worker.fetch(new Request('https://api.tapbot.internal/api/v1/bots'), env);
        const publicBots3: any = await publicCheck3.json();
        expect(publicBots3.data.find((b: any) => b.slug === 'e2e-music-bot')).toBeUndefined();
    });

    // -------------------------------------------------------------
    // Public Endpoints & CORS (Preserving existing functionality)
    // -------------------------------------------------------------
    it('handles CORS OPTIONS preflight', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots', {
            method: 'OPTIONS'
        });
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(204);
        expect(res.headers.get('Access-Control-Allow-Origin')).toBe('*');
    });

    it('returns categories list (GET /api/v1/categories)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/categories');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data).toHaveLength(2);
    });

    it('returns published bots list (GET /api/v1/bots)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data[0].slug).toBe('ping-pong-bot');
    });

    it('returns single bot details by slug (GET /api/v1/bots/:id)', async () => {
        const req = new Request('https://api.tapbot.internal/api/v1/bots/ping-pong-bot');
        const res = await worker.fetch(req, env);
        expect(res.status).toBe(200);

        const body: any = await res.json();
        expect(body.success).toBe(true);
        expect(body.data.name).toBe('Ping Pong Runner');
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
    });
});
