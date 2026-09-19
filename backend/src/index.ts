export interface Env {
    DB: D1Database;
    BUCKET: R2Bucket;
}

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        const url = new URL(request.url);
        const corsHeaders = {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type",
            "Content-Type": "application/json"
        };

        if (request.method === "OPTIONS") {
            return new Response(null, { headers: corsHeaders });
        }

        // GET /api/v1/bots - List all published catalog bots
        if (url.pathname === "/api/v1/bots" && request.method === "GET") {
            const { results } = await env.DB.prepare(
                "SELECT * FROM bots ORDER BY updated_at DESC"
            ).all();

            const bots = results.map((row: any) => ({
                id: row.id,
                name: row.name,
                summary: row.summary,
                description: row.description,
                author: row.author,
                version: row.version,
                iconUrl: row.icon_url,
                category: row.category,
                tags: JSON.parse(row.tags || "[]"),
                requiredCredentials: JSON.parse(row.required_credentials || "[]"),
                packageInfo: {
                    packageUrl: row.package_url,
                    sha256Checksum: row.package_sha256,
                    runtimeType: row.runtime_type,
                    entryClass: row.entry_class,
                    packageSizeBytes: row.package_size_bytes
                },
                permissionsRequired: JSON.parse(row.permissions || "[]"),
                repositoryUrl: row.repository_url
            }));

            return new Response(JSON.stringify(bots), { headers: corsHeaders });
        }

        // GET /api/v1/bots/:id - Fetch individual bot metadata
        const match = url.pathname.match(/^\/api\/v1\/bots\/([^/]+)$/);
        if (match && request.method === "GET") {
            const botId = match[1];
            const row: any = await env.DB.prepare(
                "SELECT * FROM bots WHERE id = ?"
            ).bind(botId).first();

            if (!row) {
                return new Response(JSON.stringify({ error: "Bot not found" }), {
                    status: 404,
                    headers: corsHeaders
                });
            }

            const bot = {
                id: row.id,
                name: row.name,
                summary: row.summary,
                description: row.description,
                author: row.author,
                version: row.version,
                iconUrl: row.icon_url,
                category: row.category,
                tags: JSON.parse(row.tags || "[]"),
                requiredCredentials: JSON.parse(row.required_credentials || "[]"),
                packageInfo: {
                    packageUrl: row.package_url,
                    sha256Checksum: row.package_sha256,
                    runtimeType: row.runtime_type,
                    entryClass: row.entry_class,
                    packageSizeBytes: row.package_size_bytes
                },
                permissionsRequired: JSON.parse(row.permissions || "[]"),
                repositoryUrl: row.repository_url
            };

            return new Response(JSON.stringify(bot), { headers: corsHeaders });
        }

        // GET /packages/:filename - Download package from R2
        const pkgMatch = url.pathname.match(/^\/packages\/(.+)$/);
        if (pkgMatch && request.method === "GET") {
            const filename = pkgMatch[1];
            const object = await env.BUCKET.get(filename);
            if (!object) {
                return new Response("Package not found", { status: 404 });
            }

            const headers = new Headers();
            object.writeHttpMetadata(headers);
            headers.set("etag", object.httpEtag);
            headers.set("Content-Disposition", `attachment; filename="${filename}"`);

            return new Response(object.body, { headers });
        }

        return new Response(JSON.stringify({ error: "Route not found" }), {
            status: 404,
            headers: corsHeaders
        });
    }
};
