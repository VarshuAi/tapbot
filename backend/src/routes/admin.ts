/**
 * Admin API Routes for TapBot Catalog (/api/v1/admin/...)
 */

import {
    Env,
    ApiResponse,
    BotRow,
    BotVersionRow,
    CreateBotRequest,
    UpdateBotRequest,
    CreateVersionRequest,
    CreateCredentialRequest
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

export async function handleAdminRoutes(request: Request, env: Env, url: URL): Promise<Response | null> {
    const path = url.pathname;

    // Fast check if path belongs to admin
    if (!path.startsWith('/api/v1/admin')) {
        return null;
    }

    // Enforce Admin Authentication
    const authResult = verifyAdminAuth(request, env);
    if (!authResult.authorized && authResult.errorResponse) {
        return authResult.errorResponse;
    }

    // 1. POST /api/v1/admin/bots - Create new draft bot
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

        // Check slug uniqueness
        const existing = await env.DB.prepare('SELECT id FROM bots WHERE slug = ?').bind(body.slug).first();
        if (existing) {
            return jsonResponse({
                success: false,
                error: { code: 'CONFLICT', message: `Bot with slug '${body.slug}' already exists` }
            }, 409);
        }

        const id = generateId('bot');
        const now = new Date().toISOString();
        const runtime = body.runtime || 'native_art';

        await env.DB.prepare(`
            INSERT INTO bots (id, slug, name, description, long_description, icon_url, category, runtime, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'draft', ?, ?)
        `).bind(
            id,
            body.slug,
            body.name,
            body.description,
            body.longDescription || null,
            body.iconUrl || null,
            body.category,
            runtime,
            now,
            now
        ).run();

        const created = await env.DB.prepare('SELECT * FROM bots WHERE id = ?').bind(id).first<BotRow>();

        return jsonResponse({
            success: true,
            data: created
        }, 201);
    }

    // 2. PUT /api/v1/admin/bots/:id - Update bot metadata
    const botUpdateMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)$/);
    if (botUpdateMatch && request.method === 'PUT') {
        const idOrSlug = decodeURIComponent(botUpdateMatch[1]);
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

        const name = body.name ?? bot.name;
        const description = body.description ?? bot.description;
        const longDescription = body.longDescription !== undefined ? body.longDescription : bot.long_description;
        const iconUrl = body.iconUrl !== undefined ? body.iconUrl : bot.icon_url;
        const category = body.category ?? bot.category;
        const runtime = body.runtime ?? bot.runtime;
        const now = new Date().toISOString();

        await env.DB.prepare(`
            UPDATE bots
            SET name = ?, description = ?, long_description = ?, icon_url = ?, category = ?, runtime = ?, updated_at = ?
            WHERE id = ?
        `).bind(name, description, longDescription, iconUrl, category, runtime, now, bot.id).run();

        const updated = await env.DB.prepare('SELECT * FROM bots WHERE id = ?').bind(bot.id).first<BotRow>();

        return jsonResponse({
            success: true,
            data: updated
        });
    }

    // 3. DELETE /api/v1/admin/bots/:id - Delete a bot
    const botDeleteMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)$/);
    if (botDeleteMatch && request.method === 'DELETE') {
        const idOrSlug = decodeURIComponent(botDeleteMatch[1]);
        const bot = await env.DB.prepare('SELECT id FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

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

        return jsonResponse({
            success: true,
            data: { deletedBotId: bot.id }
        });
    }

    // 4. POST /api/v1/admin/bots/:id/publish - Publish a bot
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

        return jsonResponse({
            success: true,
            data: { id: bot.id, status: 'published', updatedAt: now }
        });
    }

    // 5. POST /api/v1/admin/bots/:id/unpublish - Unpublish a bot back to draft
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
            "UPDATE bots SET status = 'draft', updated_at = ? WHERE id = ?"
        ).bind(now, bot.id).run();

        return jsonResponse({
            success: true,
            data: { id: bot.id, status: 'draft', updatedAt: now }
        });
    }

    // 6. POST /api/v1/admin/bots/:id/versions - Register new bot version
    const versionMatch = path.match(/^\/api\/v1\/admin\/bots\/([^/]+)\/versions$/);
    if (versionMatch && request.method === 'POST') {
        const idOrSlug = decodeURIComponent(versionMatch[1]);
        const bot = await env.DB.prepare('SELECT id FROM bots WHERE id = ? OR slug = ?').bind(idOrSlug, idOrSlug).first<BotRow>();

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

        await env.DB.batch([
            env.DB.prepare(`
                INSERT INTO bot_versions (id, bot_id, version, package_key, package_size, sha256, release_notes, minimum_app_version, published_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            `).bind(
                versionId,
                bot.id,
                body.version,
                body.packageKey,
                body.packageSize,
                body.sha256,
                body.releaseNotes || null,
                minAppVersion,
                now
            ),
            env.DB.prepare(`
                UPDATE bots SET current_version = ?, updated_at = ? WHERE id = ?
            `).bind(body.version, now, bot.id)
        ]);

        const createdVersion = await env.DB.prepare('SELECT * FROM bot_versions WHERE id = ?').bind(versionId).first<BotVersionRow>();

        return jsonResponse({
            success: true,
            data: createdVersion
        }, 201);
    }

    // 7. POST /api/v1/admin/bots/:id/credentials - Add/update bot credential spec
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

        const credId = generateId('cred');
        const required = body.required !== false ? 1 : 0;
        const secret = body.secret !== false ? 1 : 0;
        const inputType = body.inputType || 'text';

        // Upsert credential for key
        await env.DB.prepare(`
            INSERT INTO bot_credentials (id, bot_id, key, display_name, description, required, secret, input_type)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).bind(
            credId,
            bot.id,
            body.key,
            body.displayName,
            body.description || null,
            required,
            secret,
            inputType
        ).run();

        return jsonResponse({
            success: true,
            data: { id: credId, botId: bot.id, key: body.key, displayName: body.displayName }
        }, 201);
    }

    // 8. PUT /api/v1/admin/packages/:key - Upload package archive to R2
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
