/**
 * TapBot Catalog & Package Backend
 * Powered by Cloudflare Workers, Cloudflare D1, and Cloudflare R2
 */

import { Env, ApiResponse } from './types';
import { handleCorsPreflight, withCors } from './middleware/cors';
import { handlePublicRoutes } from './routes/public';
import { handleAdminRoutes } from './routes/admin';
import { renderDashboardHtml } from './dashboard/html';

export type { Env };

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        // 1. Handle CORS Preflight
        if (request.method === 'OPTIONS') {
            return handleCorsPreflight();
        }

        const url = new URL(request.url);

        try {
            // 2. Serve Web-Based Admin Dashboard SPA (/admin)
            if (
                request.method === 'GET' &&
                (url.pathname === '/admin' || url.pathname === '/admin/' || url.pathname === '/admin.html')
            ) {
                const html = renderDashboardHtml();
                return new Response(html, {
                    status: 200,
                    headers: {
                        'Content-Type': 'text/html; charset=utf-8',
                        'X-Content-Type-Options': 'nosniff',
                        'X-Frame-Options': 'DENY',
                        'Referrer-Policy': 'strict-origin-when-cross-origin',
                        'Cache-Control': 'no-cache, no-store, must-revalidate'
                    }
                });
            }

            // 3. Dispatch Admin API Routes (/api/v1/admin/...)
            const adminResponse = await handleAdminRoutes(request, env, url);
            if (adminResponse) {
                return withCors(adminResponse);
            }

            // 4. Dispatch Public Routes (/api/v1/...)
            const publicResponse = await handlePublicRoutes(request, env, url);
            if (publicResponse) {
                return withCors(publicResponse);
            }

            // 5. Default 404 Not Found
            const notFoundPayload: ApiResponse = {
                success: false,
                error: {
                    code: 'NOT_FOUND',
                    message: `Route not found: ${request.method} ${url.pathname}`
                }
            };

            return withCors(new Response(JSON.stringify(notFoundPayload), {
                status: 404,
                headers: { 'Content-Type': 'application/json' }
            }));
        } catch (error: any) {
            console.error('Unhandled worker error:', error);

            const errorPayload: ApiResponse = {
                success: false,
                error: {
                    code: 'INTERNAL_SERVER_ERROR',
                    message: error.message || 'An unexpected error occurred'
                }
            };

            return withCors(new Response(JSON.stringify(errorPayload), {
                status: 500,
                headers: { 'Content-Type': 'application/json' }
            }));
        }
    }
};
