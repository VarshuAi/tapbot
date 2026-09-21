/**
 * Public API Routes for TapBot Catalog (/api/v1/...)
 */

import {
    Env,
    ApiResponse,
    BotRow,
    BotVersionRow,
    BotCredentialRow,
    CategoryRow,
    BotDetailDto,
    BotSummaryDto,
    BotCredentialDto,
    BotVersionDto
} from '../types';

function jsonResponse<T>(data: ApiResponse<T>, status = 200): Response {
    return new Response(JSON.stringify(data), {
        status,
        headers: { 'Content-Type': 'application/json' }
    });
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

export async function handlePublicRoutes(request: Request, env: Env, url: URL): Promise<Response | null> {
    const path = url.pathname;
    const baseUrl = url.origin;

    // 1. GET /api/v1/categories - List all catalog categories
    if (path === '/api/v1/categories' && request.method === 'GET') {
        const { results } = await env.DB.prepare(
            'SELECT id, name, slug FROM categories ORDER BY name ASC'
        ).all<CategoryRow>();

        return jsonResponse({
            success: true,
            data: results || []
        });
    }

    // 2. GET /api/v1/bots - List published bots
    if (path === '/api/v1/bots' && request.method === 'GET') {
        const categoryFilter = url.searchParams.get('category');
        const searchQuery = url.searchParams.get('search')?.trim().toLowerCase();

        let query = "SELECT * FROM bots WHERE status = 'published'";
        const params: any[] = [];

        if (categoryFilter && categoryFilter !== 'all') {
            query += ' AND category = ?';
            params.push(categoryFilter);
        }

        if (searchQuery) {
            query += ' AND (LOWER(name) LIKE ? OR LOWER(description) LIKE ? OR LOWER(slug) LIKE ?)';
            const wildcard = `%${searchQuery}%`;
            params.push(wildcard, wildcard, wildcard);
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

        // Fetch credentials and current version for each bot to provide rich catalog items
        const botSummaries: BotSummaryDto[] = await Promise.all(
            bots.map(async (bot) => {
                const credsStmt = env.DB.prepare(
                    'SELECT * FROM bot_credentials WHERE bot_id = ? ORDER BY key ASC'
                ).bind(bot.id);
                const { results: credRows } = await credsStmt.all<BotCredentialRow>();

                let currentVersionInfo: BotVersionDto | null = null;
                if (bot.current_version) {
                    const verStmt = env.DB.prepare(
                        'SELECT * FROM bot_versions WHERE bot_id = ? AND version = ?'
                    ).bind(bot.id, bot.current_version);
                    const verRow = await verStmt.first<BotVersionRow>();
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
                    credentials: (credRows || []).map(mapCredentialRow),
                    currentVersionInfo
                };
            })
        );

        return jsonResponse({
            success: true,
            data: botSummaries
        });
    }

    // 3a. GET /api/v1/bots/:id/versions/latest - Get latest published version
    const latestVersionMatch = path.match(/^\/api\/v1\/bots\/([^/]+)\/versions\/latest$/);
    if (latestVersionMatch && request.method === 'GET') {
        const idOrSlug = decodeURIComponent(latestVersionMatch[1]);

        const bot = await env.DB.prepare(
            'SELECT id FROM bots WHERE id = ? OR slug = ?'
        ).bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `Bot not found with identifier: ${idOrSlug}`
                }
            }, 404);
        }

        const latestVersion = await env.DB.prepare(
            "SELECT * FROM bot_versions WHERE bot_id = ? AND status = 'published' ORDER BY published_at DESC LIMIT 1"
        ).bind(bot.id).first<BotVersionRow>();

        if (!latestVersion) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `No published version found for bot: ${idOrSlug}`
                }
            }, 404);
        }

        return jsonResponse({
            success: true,
            data: mapVersionRow(latestVersion, baseUrl)
        });
    }

    // 3b. GET /api/v1/bots/:id/versions - List published version changelog for a bot
    const versionsMatch = path.match(/^\/api\/v1\/bots\/([^/]+)\/versions$/);
    if (versionsMatch && request.method === 'GET') {
        const idOrSlug = decodeURIComponent(versionsMatch[1]);

        // Resolve bot
        const bot = await env.DB.prepare(
            'SELECT id FROM bots WHERE id = ? OR slug = ?'
        ).bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `Bot not found with identifier: ${idOrSlug}`
                }
            }, 404);
        }

        const { results: versions } = await env.DB.prepare(
            "SELECT * FROM bot_versions WHERE bot_id = ? AND status = 'published' ORDER BY published_at DESC"
        ).bind(bot.id).all<BotVersionRow>();

        const dtos: BotVersionDto[] = (versions || []).map((row) => mapVersionRow(row, baseUrl));

        return jsonResponse({
            success: true,
            data: dtos
        });
    }

    // 4. GET /api/v1/bots/:id - Get single bot details
    const botDetailMatch = path.match(/^\/api\/v1\/bots\/([^/]+)$/);
    if (botDetailMatch && request.method === 'GET') {
        const idOrSlug = decodeURIComponent(botDetailMatch[1]);

        const bot = await env.DB.prepare(
            "SELECT * FROM bots WHERE (id = ? OR slug = ?) AND status = 'published'"
        ).bind(idOrSlug, idOrSlug).first<BotRow>();

        if (!bot) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `Published bot not found with identifier: ${idOrSlug}`
                }
            }, 404);
        }

        // Fetch credentials
        const { results: credRows } = await env.DB.prepare(
            'SELECT * FROM bot_credentials WHERE bot_id = ? ORDER BY key ASC'
        ).bind(bot.id).all<BotCredentialRow>();

        // Fetch current version info
        let currentVersionInfo: BotVersionDto | null = null;
        if (bot.current_version) {
            const verRow = await env.DB.prepare(
                'SELECT * FROM bot_versions WHERE bot_id = ? AND version = ?'
            ).bind(bot.id, bot.current_version).first<BotVersionRow>();

            if (verRow) {
                currentVersionInfo = mapVersionRow(verRow, baseUrl);
            }
        }

        const detail: BotDetailDto = {
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
            credentials: (credRows || []).map(mapCredentialRow),
            currentVersionInfo
        };

        return jsonResponse({
            success: true,
            data: detail
        });
    }

    // 5. GET /api/v1/packages/:key - Download package archive from R2
    const pkgMatch = path.match(/^\/api\/v1\/packages\/(.+)$/);
    if (pkgMatch && request.method === 'GET') {
        const rawKey = decodeURIComponent(pkgMatch[1]);
        const object = await env.BUCKET.get(rawKey);

        if (!object) {
            return jsonResponse({
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `Package not found in storage: ${rawKey}`
                }
            }, 404);
        }

        const headers = new Headers();
        object.writeHttpMetadata(headers);
        headers.set('etag', object.httpEtag);
        const contentType = object.httpMetadata?.contentType || 'application/octet-stream';
        headers.set('Content-Type', contentType);
        const isImage = contentType.startsWith('image/');
        const disposition = isImage
            ? 'inline'
            : `attachment; filename="${rawKey.split('/').pop() || 'package.botpkg'}"`;
        headers.set('Content-Disposition', disposition);
        headers.set('Cache-Control', 'public, max-age=31536000, immutable');

        return new Response(object.body, { headers });
    }

    return null; // Route not matched by public handler
}
