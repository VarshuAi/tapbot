/**
 * Admin Authentication Middleware
 */

import { Env, ApiResponse } from '../types';

export function verifyAdminAuth(request: Request, env: Env): { authorized: boolean; errorResponse?: Response } {
    const configuredKey = env.ADMIN_API_KEY || 'dev-admin-secret-key-change-in-prod';
    
    // Check Authorization: Bearer <key>
    const authHeader = request.headers.get('Authorization');
    let providedKey: string | null = null;

    if (authHeader && authHeader.startsWith('Bearer ')) {
        providedKey = authHeader.substring(7).trim();
    }

    // Fallback to X-Admin-Key header
    if (!providedKey) {
        providedKey = request.headers.get('X-Admin-Key')?.trim() || null;
    }

    if (!providedKey || providedKey !== configuredKey) {
        const payload: ApiResponse = {
            success: false,
            error: {
                code: 'UNAUTHORIZED',
                message: 'Unauthorized: Valid admin API key required in Authorization or X-Admin-Key header'
            }
        };

        return {
            authorized: false,
            errorResponse: new Response(JSON.stringify(payload), {
                status: 401,
                headers: {
                    'Content-Type': 'application/json'
                }
            })
        };
    }

    return { authorized: true };
}
