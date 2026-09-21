/**
 * Admin API Routes for TapBot Catalog (/api/v1/admin/...)
 */

import {
    Env,
    ApiResponse,
    BotRow,
    BotVersionRow,
    BotCredentialRow,
    AuditLogRow,
    AuditLogDto,
    AdminStatsDto,
    BotLifecycleStatus,
    CreateBotRequest,
    UpdateBotRequest,
    SetLifecycleRequest,
    CreateVersionRequest,
    CreateCredentialRequest,
    BotCredentialDto,
    BotVersionDto
} from '../types';
import { verifyAdminAuth } from '../middleware/auth';

function jsonResponse<T>(data: ApiResponse<T>, status = 200): Response {
    return new Response(JSON.stringify(data), {
        status,
        headers: { 'Content-Type': 'application/json' }
    });
}

function generateId(prefix: string): string {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
}

async function recordAuditLog(
    env: Env,
    action: string,
    entityType: string,
    entityId: string,
    details?: any,
    actor = 'admin'
): Promise<void> {
    try {
        const id = generateId('audit');
        const now = new Date().toISOString();
        const detailsStr = details ? JSON.stringify(details) : null;

        await env.DB.prepare(`
            INSERT INTO audit_logs (id, action, entity_type, entity_id, details, actor, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `).bind(id, action, entityType, entityId, detailsStr, actor, now).run();
    } catch (err) {
        console.error('Failed to record audit log:', err);
    }
}

function mapCredentialRow(row: BotCredentialRow): BotCredentialDto {
    return {
        key: row.key,
        displayName: row.display_name,
        description: row.description,
        required: row.required === 1,
        secret: row.secret === 1,
        inputType: row.input_type
    };
}

function mapVersionRow(row: BotVersionRow, baseUrl: string): BotVersionDto {
    return {
        id: row.id,
        version: row.version,
        packageKey: row.package_key,
        packageSize: row.package_size,
        sha256: row.sha256,
        releaseNotes: row.release_notes,
        minimumAppVersion: row.minimum_app_version,
        minimumRuntimeVersion: row.minimum_runtime_version || '1.0.0',
        status: row.status || 'published',
        publishedAt: row.published_at,
        downloadUrl: `${baseUrl}/api/v1/packages/${encodeURIComponent(row.package_key)}`
    };
}

function normalizeLifecycle(status: string): BotLifecycleStatus | null {
    const s = status.trim().toLowerCase();
    if (['draft', 'review', 'published', 'unpublished', 'archived'].includes(s)) {
        return s as BotLifecycleStatus;
    }
    return null;
}

export async function handleAdminRoutes(request: Request, env: Env, url: URL): Promise<Response | null> {
    const path = url.pathname;
    const baseUrl = url.origin;

    // Fast check if path belongs to admin API
    if (!path.startsWith('/api/v1/admin')) {
        return null;
    }

    // Enforce Admin Authentication
    const authResult = verifyAdminAuth(request, env);
    if (!authResult.authorized && authResult.errorResponse) {
        return authResult.errorResponse;
    }

    // -----------------------------------------------------------------
    // 1. GET /api/v1/admin/stats - Overview statistics & health
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/stats' && request.method === 'GET') {
        const botsCount = await env.DB.prepare('SELECT count(*) as total FROM bots').first<{ total: number }>();
        const versionsCount = await env.DB.prepare('SELECT count(*) as total FROM bot_versions').first<{ total: number }>();

        // Status breakdown
        const statusRows = await env.DB.prepare(
            'SELECT status, count(*) as count FROM bots GROUP BY status'
        ).all<{ status: string; count: number }>();

        const breakdown: Record<string, number> = {
            draft: 0,
            review: 0,
            published: 0,
            unpublished: 0,
            archived: 0
        };

        if (statusRows.results) {
            for (const r of statusRows.results) {
                const s = r.status.toLowerCase();
                if (breakdown[s] !== undefined) {
                    breakdown[s] = r.count;
                }
            }
        }

        const stats: AdminStatsDto = {
            totalBots: botsCount?.total || 0,
            publishedBots: breakdown.published,
            draftBots: breakdown.draft,
            reviewBots: breakdown.review,
            unpublishedBots: breakdown.unpublished,
            archivedBots: breakdown.archived,
            totalVersions: versionsCount?.total || 0,
            downloads: 'Not available',
            installs: 'Not available',
            lifecycleBreakdown: {
                draft: breakdown.draft,
                review: breakdown.review,
                published: breakdown.published,
                unpublished: breakdown.unpublished,
                archived: breakdown.archived
            }
        };

        return jsonResponse({
            success: true,
            data: stats
        });
    }

    // -----------------------------------------------------------------
    // 2. GET /api/v1/admin/audit - List audit trail logs
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/audit' && request.method === 'GET') {
        const limitParam = parseInt(url.searchParams.get('limit') || '50', 10);
        const limit = Math.min(Math.max(1, limitParam), 100);

        const { results } = await env.DB.prepare(
            'SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT ?'
        ).bind(limit).all<AuditLogRow>();

        const logs: AuditLogDto[] = (results || []).map((row) => {
            let parsedDetails: any = null;
            if (row.details) {
                try {
                    parsedDetails = JSON.parse(row.details);
                } catch {
                    parsedDetails = row.details;
                }
            }
            return {
                id: row.id,
                action: row.action,
                entityType: row.entity_type,
                entityId: row.entity_id,
                details: parsedDetails,
                actor: row.actor,
                createdAt: row.created_at
            };
        });

        return jsonResponse({
            success: true,
            data: logs
        });
    }

    // -----------------------------------------------------------------
    // 3. GET /api/v1/admin/bots - List all bots (all lifecycle states)
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/bots' && request.method === 'GET') {
        const statusFilter = url.searchParams.get('status')?.trim().toLowerCase();
        let query = 'SELECT * FROM bots';
        const params: any[] = [];

        if (statusFilter && statusFilter !== 'all') {
            query += ' WHERE status = ?';
            params.push(statusFilter);
        }

        query += ' ORDER BY updated_at DESC';

        const stmt = params.length > 0
            ? env.DB.prepare(query).bind(...params)
            : env.DB.prepare(query);

        const { results: bots } = await stmt.all<BotRow>();

        if (!bots || bots.length === 0) {
            return jsonResponse({
                success: true,
                data: []
            });
        }

        const items = await Promise.all(
            bots.map(async (bot) => {
                const creds = await env.DB.prepare(
                    'SELECT * FROM bot_credentials WHERE bot_id = ? ORDER BY key ASC'
                ).bind(bot.id).all<BotCredentialRow>();

                const verCount = await env.DB.prepare(
                    'SELECT count(*) as count FROM bot_versions WHERE bot_id = ?'
                ).bind(bot.id).first<{ count: number }>();

                let currentVersionInfo: BotVersionDto | null = null;
                if (bot.current_version) {
                    const verRow = await env.DB.prepare(
                        'SELECT * FROM bot_versions WHERE bot_id = ? AND version = ?'
                    ).bind(bot.id, bot.current_version).first<BotVersionRow>();
                    if (verRow) {
                        currentVersionInfo = mapVersionRow(verRow, baseUrl);
                    }
                }

                return {
                    id: bot.id,
                    slug: bot.slug,
                    name: bot.name,
                    description: bot.description,
                    longDescription: bot.long_description,
                    iconUrl: bot.icon_url,
                    category: bot.category,
                    runtime: bot.runtime,
                    currentVersion: bot.current_version,
                    status: bot.status,
                    createdAt: bot.created_at,
                    updatedAt: bot.updated_at,
                    credentials: (creds.results || []).map(mapCredentialRow),
                    currentVersionInfo,
                    versionsCount: verCount?.count || 0
                };
            })
        );

        return jsonResponse({
            success: true,
            data: items
        });
    }

    // -----------------------------------------------------------------
    // 4. POST /api/v1/admin/bots - Create new bot
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/bots' && request.method === 'POST') {
        let body: CreateBotRequest;
        try {
            body = await request.json();
        } catch {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Invalid JSON body' }
            }, 400);
        }

        if (!body.name || !body.slug || !body.description || !body.category) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'VALIDATION_ERROR',
                    message: 'Missing required fields: name, slug, description, category'
                }
            }, 400);
        }

        const slug = body.slug.trim().toLowerCase();

        // Check slug uniqueness
        const existing = await env.DB.prepare('SELECT id FROM bots WHERE slug = ?').bind(slug).first();
        if (existing) {
            return jsonResponse({
                success: false,
                error: { code: 'CONFLICT', message: `Bot with slug '${slug}' already exists` }
            }, 409);
        }

        const id = generateId('bot');
        const now = new Date().toISOString();
        const runtime = body.runtime || 'native_art';
        const rawStatus = body.status ? normalizeLifecycle(body.status) : 'draft';
        const status = rawStatus || 'draft';

        await env.DB.prepare(`
            INSERT INTO bots (id, slug, name, description, long_description, icon_url, category, runtime, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `).bind(
            id,
            slug,
            body.name.trim(),
            body.description.trim(),
            body.longDescription ? body.longDescription.trim() : null,
            body.iconUrl ? body.iconUrl.trim() : null,
            body.category.trim(),
            runtime,
            status,
            now,
            now
        ).run();

        await recordAuditLog(env, 'create_bot', 'bot', id, {
            slug,
            name: body.name,
            category: body.category,
            status,
            runtime
        });

        const created = await env.DB.prepare('SELECT * FROM bots WHERE id = ?').bind(id).first<BotRow>();

        return jsonResponse({
            success: true,
            data: created
        }, 201);
    }

    // -----------------------------------------------------------------
    // 5. GET /api/v1/admin/bots/:id - Get single bot with versions & credentials
    // -----------------------------------------------------------------
    const botDetailMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)$/);
    if (botDetailMatch && request.method === 'GET') {
        const idOrSlug = decodeURIComponent(botDetailMatch[1]);
        const bot = await env.DB.prepare('SELECT * FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        const creds = await env.DB.prepare(
            'SELECT * FROM bot_credentials WHERE bot_id = ? ORDER BY key ASC'
        ).bind(bot.id).all<BotCredentialRow>();

        const versions = await env.DB.prepare(
            'SELECT * FROM bot_versions WHERE bot_id = ? ORDER BY published_at DESC'
        ).bind(bot.id).all<BotVersionRow>();

        const versionDtos = (versions.results || []).map((v) => mapVersionRow(v, baseUrl));
        const currentVersionInfo = versionDtos.find((v) => v.version === bot.current_version) || null;

        return jsonResponse({
            success: true,
            data: {
                id: bot.id,
                slug: bot.slug,
                name: bot.name,
                description: bot.description,
                longDescription: bot.long_description,
                iconUrl: bot.icon_url,
                category: bot.category,
                runtime: bot.runtime,
                currentVersion: bot.current_version,
                status: bot.status,
                createdAt: bot.created_at,
                updatedAt: bot.updated_at,
                credentials: (creds.results || []).map(mapCredentialRow),
                currentVersionInfo,
                versions: versionDtos,
                versionsCount: versionDtos.length
            }
        });
    }

    // -----------------------------------------------------------------
    // 6. PUT /api/v1/admin/bots/:id - Update bot metadata
    // -----------------------------------------------------------------
    if (botDetailMatch && request.method === 'PUT') {
        const idOrSlug = decodeURIComponent(botDetailMatch[1]);
        const bot = await env.DB.prepare('SELECT * FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        let body: UpdateBotRequest;
        try {
            body = await request.json();
        } catch {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Invalid JSON body' }
            }, 400);
        }

        const name = body.name !== undefined ? body.name.trim() : bot.name;
        const description = body.description !== undefined ? body.description.trim() : bot.description;
        const longDescription = body.longDescription !== undefined ? (body.longDescription ? body.longDescription.trim() : null) : bot.long_description;
        const iconUrl = body.iconUrl !== undefined ? (body.iconUrl ? body.iconUrl.trim() : null) : bot.icon_url;
        const category = body.category !== undefined ? body.category.trim() : bot.category;
        const runtime = body.runtime !== undefined ? body.runtime.trim() : bot.runtime;

        let status = bot.status;
        if (body.status) {
            const normalized = normalizeLifecycle(body.status);
            if (!normalized) {
                return jsonResponse({
                    success: false,
                    error: {
                        code: 'VALIDATION_ERROR',
                        message: `Invalid status '${body.status}'. Must be one of: draft, review, published, unpublished, archived`
                    }
                }, 400);
            }
            status = normalized;
        }

        const now = new Date().toISOString();

        await env.DB.prepare(`
            UPDATE bots
            SET name = ?, description = ?, long_description = ?, icon_url = ?, category = ?, runtime = ?, status = ?, updated_at = ?
            WHERE id = ?
        `).bind(name, description, longDescription, iconUrl, category, runtime, status, now, bot.id).run();

        await recordAuditLog(env, 'update_bot', 'bot', bot.id, {
            name,
            category,
            runtime,
            status,
            previousStatus: bot.status
        });

        const updated = await env.DB.prepare('SELECT * FROM bots WHERE id = ?').bind(bot.id).first<BotRow>();

        return jsonResponse({
            success: true,
            data: updated
        });
    }

    // -----------------------------------------------------------------
    // 7. DELETE /api/v1/admin/bots/:id - Delete a bot and dependencies
    // -----------------------------------------------------------------
    if (botDetailMatch && request.method === 'DELETE') {
        const idOrSlug = decodeURIComponent(botDetailMatch[1]);
        const bot = await env.DB.prepare('SELECT id, slug, name FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        await env.DB.batch([
            env.DB.prepare('DELETE FROM bot_credentials WHERE bot_id = ?').bind(bot.id),
            env.DB.prepare('DELETE FROM bot_versions WHERE bot_id = ?').bind(bot.id),
            env.DB.prepare('DELETE FROM bots WHERE id = ?').bind(bot.id)
        ]);

        await recordAuditLog(env, 'delete_bot', 'bot', bot.id, {
            slug: bot.slug,
            name: bot.name
        });

        return jsonResponse({
            success: true,
            data: { deletedBotId: bot.id, slug: bot.slug }
        });
    }

    // -----------------------------------------------------------------
    // 8. POST /api/v1/admin/bots/:id/lifecycle - Update lifecycle state
    // -----------------------------------------------------------------
    const lifecycleMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/lifecycle$/);
    if (lifecycleMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(lifecycleMatch[1]);
        const bot = await env.DB.prepare('SELECT * FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        let body: SetLifecycleRequest;
        try {
            body = await request.json();
        } catch {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Invalid JSON body' }
            }, 400);
        }

        if (!body.status) {
            return jsonResponse({
                success: false,
                error: { code: 'VALIDATION_ERROR', message: 'Missing required status field' }
            }, 400);
        }

        const newStatus = normalizeLifecycle(body.status);
        if (!newStatus) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'VALIDATION_ERROR',
                    message: `Invalid lifecycle status: '${body.status}'. Expected: DRAFT, REVIEW, PUBLISHED, UNPUBLISHED, ARCHIVED`
                }
            }, 400);
        }

        const now = new Date().toISOString();
        await env.DB.prepare(
            'UPDATE bots SET status = ?, updated_at = ? WHERE id = ?'
        ).bind(newStatus, now, bot.id).run();

        await recordAuditLog(env, 'set_lifecycle', 'bot', bot.id, {
            from: bot.status,
            to: newStatus,
            reason: body.reason || null
        });

        return jsonResponse({
            success: true,
            data: {
                id: bot.id,
                slug: bot.slug,
                previousStatus: bot.status,
                status: newStatus,
                updatedAt: now
            }
        });
    }

    // -----------------------------------------------------------------
    // 9. POST /api/v1/admin/bots/:id/publish - Publish shortcut
    // -----------------------------------------------------------------
    const publishMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/publish$/);
    if (publishMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(publishMatch[1]);
        const bot = await env.DB.prepare('SELECT * FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        const now = new Date().toISOString();
        await env.DB.prepare(
            "UPDATE bots SET status = 'published', updated_at = ? WHERE id = ?"
        ).bind(now, bot.id).run();

        await recordAuditLog(env, 'publish_bot', 'bot', bot.id, {
            from: bot.status,
            to: 'published'
        });

        return jsonResponse({
            success: true,
            data: { id: bot.id, status: 'published', updatedAt: now }
        });
    }

    // -----------------------------------------------------------------
    // 10. POST /api/v1/admin/bots/:id/unpublish - Unpublish shortcut
    // -----------------------------------------------------------------
    const unpublishMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/unpublish$/);
    if (unpublishMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(unpublishMatch[1]);
        const bot = await env.DB.prepare('SELECT * FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        const now = new Date().toISOString();
        await env.DB.prepare(
            "UPDATE bots SET status = 'unpublished', updated_at = ? WHERE id = ?"
        ).bind(now, bot.id).run();

        await recordAuditLog(env, 'unpublish_bot', 'bot', bot.id, {
            from: bot.status,
            to: 'unpublished'
        });

        return jsonResponse({
            success: true,
            data: { id: bot.id, status: 'unpublished', updatedAt: now }
        });
    }

    // -----------------------------------------------------------------
    // 11. POST /api/v1/admin/bots/:id/versions - Register new bot version
    // -----------------------------------------------------------------
    const versionMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/versions$/);
    if (versionMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(versionMatch[1]);
        const bot = await env.DB.prepare('SELECT id, slug, current_version FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        let body: CreateVersionRequest;
        try {
            body = await request.json();
        } catch {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Invalid JSON body' }
            }, 400);
        }

        if (!body.version || !body.packageKey || !body.packageSize || !body.sha256) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'VALIDATION_ERROR',
                    message: 'Missing required fields: version, packageKey, packageSize, sha256'
                }
            }, 400);
        }

        const versionId = generateId('ver');
        const now = new Date().toISOString();
        const minAppVersion = body.minimumAppVersion || 1;
        const minRuntimeVersion = body.minimumRuntimeVersion || '1.0.0';
        const versionStatus = body.status === 'unpublished' ? 'unpublished' : 'published';

        const statements = [
            env.DB.prepare(`
                INSERT INTO bot_versions (id, bot_id, version, package_key, package_size, sha256, release_notes, minimum_app_version, minimum_runtime_version, status, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            `).bind(
                versionId,
                bot.id,
                body.version.trim(),
                body.packageKey.trim(),
                body.packageSize,
                body.sha256.trim(),
                body.releaseNotes ? body.releaseNotes.trim() : null,
                minAppVersion,
                minRuntimeVersion,
                versionStatus,
                now
            )
        ];

        if (versionStatus === 'published') {
            statements.push(
                env.DB.prepare(`
                    UPDATE bots SET current_version = ?, updated_at = ? WHERE id = ?
                `).bind(body.version.trim(), now, bot.id)
            );
        }

        await env.DB.batch(statements);

        await recordAuditLog(env, 'create_version', 'version', versionId, {
            botId: bot.id,
            version: body.version,
            status: versionStatus,
            packageKey: body.packageKey,
            sha256: body.sha256
        });

        const createdVersion = await env.DB.prepare('SELECT * FROM bot_versions WHERE id = ?').bind(versionId).first<BotVersionRow>();

        return jsonResponse({
            success: true,
            data: createdVersion ? mapVersionRow(createdVersion, baseUrl) : null
        }, 201);
    }

    // -----------------------------------------------------------------
    // 12. POST /api/v1/admin/bots/:id/versions/:version/publish - Publish specific version
    // -----------------------------------------------------------------
    const publishVersionMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/versions\/([^/]+)\/publish$/);
    if (publishVersionMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(publishVersionMatch[1]);
        const targetVersion = decodeURIComponent(publishVersionMatch[2]);

        const bot = await env.DB.prepare('SELECT id FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();
        if (!bot) {
            return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` } }, 404);
        }

        const versionRow = await env.DB.prepare(
            'SELECT * FROM bot_versions WHERE bot_id = ? AND version = ?'
        ).bind(bot.id, targetVersion).first<BotVersionRow>();
        if (!versionRow) {
            return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: `Version not found: ${targetVersion}` } }, 404);
        }

        const now = new Date().toISOString();
        await env.DB.batch([
            env.DB.prepare("UPDATE bot_versions SET status = 'published', published_at = ? WHERE id = ?").bind(now, versionRow.id),
            env.DB.prepare("UPDATE bots SET current_version = ?, updated_at = ? WHERE id = ?").bind(targetVersion, now, bot.id)
        ]);

        await recordAuditLog(env, 'publish_version', 'version', versionRow.id, {
            botId: bot.id,
            version: targetVersion
        });

        return jsonResponse({
            success: true,
            data: { botId: bot.id, version: targetVersion, status: 'published', publishedAt: now }
        });
    }

    // -----------------------------------------------------------------
    // 13. POST /api/v1/admin/bots/:id/versions/:version/unpublish - Unpublish version
    // -----------------------------------------------------------------
    const unpublishVersionMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/versions\/([^/]+)\/unpublish$/);
    if (unpublishVersionMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(unpublishVersionMatch[1]);
        const targetVersion = decodeURIComponent(unpublishVersionMatch[2]);

        const bot = await env.DB.prepare('SELECT id, current_version FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();
        if (!bot) {
            return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` } }, 404);
        }

        const versionRow = await env.DB.prepare(
            'SELECT * FROM bot_versions WHERE bot_id = ? AND version = ?'
        ).bind(bot.id, targetVersion).first<BotVersionRow>();
        if (!versionRow) {
            return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: `Version not found: ${targetVersion}` } }, 404);
        }

        const now = new Date().toISOString();
        const statements = [
            env.DB.prepare("UPDATE bot_versions SET status = 'unpublished' WHERE id = ?").bind(versionRow.id)
        ];

        if (bot.current_version === targetVersion) {
            const nextBest = await env.DB.prepare(
                "SELECT version FROM bot_versions WHERE bot_id = ? AND version != ? AND status = 'published' ORDER BY published_at DESC LIMIT 1"
            ).bind(bot.id, targetVersion).first<{ version: string }>();

            const newCurrent = nextBest ? nextBest.version : null;
            statements.push(
                env.DB.prepare("UPDATE bots SET current_version = ?, updated_at = ? WHERE id = ?").bind(newCurrent, now, bot.id)
            );
        }

        await env.DB.batch(statements);

        await recordAuditLog(env, 'unpublish_version', 'version', versionRow.id, {
            botId: bot.id,
            version: targetVersion
        });

        return jsonResponse({
            success: true,
            data: { botId: bot.id, version: targetVersion, status: 'unpublished' }
        });
    }

    // -----------------------------------------------------------------
    // 14. POST /api/v1/admin/bots/:id/credentials - Add/update bot credential
    // -----------------------------------------------------------------
    const credMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/credentials$/);
    if (credMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(credMatch[1]);
        const bot = await env.DB.prepare('SELECT id FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` }
            }, 404);
        }

        let body: CreateCredentialRequest;
        try {
            body = await request.json();
        } catch {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Invalid JSON body' }
            }, 400);
        }

        if (!body.key || !body.displayName) {
            return jsonResponse({
                success: false,
                error: { code: 'VALIDATION_ERROR', message: 'Missing required fields: key, displayName' }
            }, 400);
        }

        const credKey = body.key.trim();
        const credId = generateId('cred');
        const required = body.required !== false ? 1 : 0;
        const secret = body.secret !== false ? 1 : 0;
        const inputType = body.inputType || 'text';

        // Delete existing credential with this key if any, then insert
        await env.DB.prepare('DELETE FROM bot_credentials WHERE bot_id = ? AND key = ?').bind(bot.id, credKey).run();

        await env.DB.prepare(`
            INSERT INTO bot_credentials (id, bot_id, key, display_name, description, required, secret, input_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).bind(
            credId,
            bot.id,
            credKey,
            body.displayName.trim(),
            body.description ? body.description.trim() : null,
            required,
            secret,
            inputType
        ).run();

        await recordAuditLog(env, 'create_credential', 'credential', credId, {
            botId: bot.id,
            key: credKey,
            displayName: body.displayName,
            required: required === 1,
            secret: secret === 1
        });

        return jsonResponse({
            success: true,
            data: { id: credId, botId: bot.id, key: credKey, displayName: body.displayName }
        }, 201);
    }

    // -----------------------------------------------------------------
    // 15. DELETE /api/v1/admin/bots/:id/credentials/:key - Delete credential
    // -----------------------------------------------------------------
    const credDeleteMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/credentials\/([^/]+)$/);
    if (credDeleteMatch && request.method === 'DELETE') {
        const idOrSlug = decodeURIComponent(credDeleteMatch[1]);
        const key = decodeURIComponent(credDeleteMatch[2]);

        const bot = await env.DB.prepare('SELECT id FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();
        if (!bot) {
            return jsonResponse({ success: false, error: { code: 'NOT_FOUND', message: `Bot not found: ${idOrSlug}` } }, 404);
        }

        await env.DB.prepare('DELETE FROM bot_credentials WHERE bot_id = ? AND key = ?').bind(bot.id, key).run();

        await recordAuditLog(env, 'delete_credential', 'credential', `${bot.id}_${key}`, {
            botId: bot.id,
            key
        });

        return jsonResponse({
            success: true,
            data: { botId: bot.id, key, deleted: true }
        });
    }

    // -----------------------------------------------------------------
    // 16. POST /api/v1/admin/upload/package - Direct package upload with auto SHA-256
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/upload/package' && request.method === 'POST') {
        let arrayBuffer: ArrayBuffer;
        let filename = 'package.botpkg';

        const contentType = request.headers.get('Content-Type') || '';

        if (contentType.includes('multipart/form-data')) {
            try {
                const formData = await request.formData();
                const file = (formData.get('package') || formData.get('file')) as File | null;
                if (!file) {
                    return jsonResponse({
                        success: false,
                        error: { code: 'BAD_REQUEST', message: "No 'package' or 'file' field found in form-data" }
                    }, 400);
                }
                filename = file.name || filename;
                arrayBuffer = await file.arrayBuffer();
            } catch (err: any) {
                return jsonResponse({
                    success: false,
                    error: { code: 'BAD_REQUEST', message: `Failed to parse form-data: ${err.message}` }
                }, 400);
            }
        } else {
            arrayBuffer = await request.arrayBuffer();
            const headerName = request.headers.get('X-Filename');
            if (headerName) {
                filename = decodeURIComponent(headerName);
            }
        }

        if (!arrayBuffer || arrayBuffer.byteLength === 0) {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Package file is empty' }
            }, 400);
        }

        // Compute SHA-256 digest
        const hashBuf = await crypto.subtle.digest('SHA-256', arrayBuffer);
        const hashArr = Array.from(new Uint8Array(hashBuf));
        const sha256 = hashArr.map((b) => b.toString(16).padStart(2, '0')).join('');

        const safeFilename = filename.replace(/[^a-zA-Z0-9._-]/g, '_');
        const packageKey = `packages/${generateId('pkg')}_${safeFilename}`;

        await env.BUCKET.put(packageKey, arrayBuffer, {
            httpMetadata: {
                contentType: 'application/octet-stream'
            }
        });

        await recordAuditLog(env, 'upload_package', 'package', packageKey, {
            packageKey,
            packageSize: arrayBuffer.byteLength,
            sha256,
            filename
        });

        return jsonResponse({
            success: true,
            data: {
                packageKey,
                packageSize: arrayBuffer.byteLength,
                sha256,
                filename
            }
        }, 201);
    }

    // -----------------------------------------------------------------
    // 17. POST /api/v1/admin/upload/icon - Direct icon image upload
    // -----------------------------------------------------------------
    if (path === '/api/v1/admin/upload/icon' && request.method === 'POST') {
        let arrayBuffer: ArrayBuffer;
        let mimeType = 'image/png';
        let filename = 'icon.png';

        const contentType = request.headers.get('Content-Type') || '';

        if (contentType.includes('multipart/form-data')) {
            try {
                const formData = await request.formData();
                const file = (formData.get('icon') || formData.get('file')) as File | null;
                if (!file) {
                    return jsonResponse({
                        success: false,
                        error: { code: 'BAD_REQUEST', message: "No 'icon' or 'file' field found in form-data" }
                    }, 400);
                }
                filename = file.name || filename;
                mimeType = file.type || mimeType;
                arrayBuffer = await file.arrayBuffer();
            } catch (err: any) {
                return jsonResponse({
                    success: false,
                    error: { code: 'BAD_REQUEST', message: `Failed to parse form-data: ${err.message}` }
                }, 400);
            }
        } else {
            arrayBuffer = await request.arrayBuffer();
            mimeType = contentType.split(';')[0].trim() || 'image/png';
            const headerName = request.headers.get('X-Filename');
            if (headerName) {
                filename = decodeURIComponent(headerName);
            }
        }

        if (!arrayBuffer || arrayBuffer.byteLength === 0) {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Icon file is empty' }
            }, 400);
        }

        const safeFilename = filename.replace(/[^a-zA-Z0-9._-]/g, '_');
        const iconKey = `icons/${generateId('icon')}_${safeFilename}`;

        await env.BUCKET.put(iconKey, arrayBuffer, {
            httpMetadata: {
                contentType: mimeType
            }
        });

        const iconUrl = `${baseUrl}/api/v1/packages/${encodeURIComponent(iconKey)}`;

        await recordAuditLog(env, 'upload_icon', 'icon', iconKey, {
            iconKey,
            iconUrl,
            size: arrayBuffer.byteLength,
            mimeType
        });

        return jsonResponse({
            success: true,
            data: {
                iconKey,
                iconUrl
            }
        }, 201);
    }

    // -----------------------------------------------------------------
    // 18. Legacy PUT /api/v1/admin/packages/:key - Raw stream upload
    // -----------------------------------------------------------------
    const pkgUploadMatch = path.match(/^\/api\/v1\/admin\/packages\/(.+)$/);
    if (pkgUploadMatch && request.method === 'PUT') {
        const rawKey = decodeURIComponent(pkgUploadMatch[1]);
        const bodyStream = request.body;

        if (!bodyStream) {
            return jsonResponse({
                success: false,
                error: { code: 'BAD_REQUEST', message: 'Missing package binary in request body' }
            }, 400);
        }

        const uploadResult = await env.BUCKET.put(rawKey, bodyStream, {
            httpMetadata: {
                contentType: 'application/octet-stream'
            }
        });

        await recordAuditLog(env, 'upload_package', 'package', rawKey, {
            packageKey: rawKey,
            size: uploadResult.size
        });

        return jsonResponse({
            success: true,
            data: {
                key: rawKey,
                size: uploadResult.size,
                etag: uploadResult.etag
            }
        }, 201);
    }

    return null; // Route not matched by admin handler
}
