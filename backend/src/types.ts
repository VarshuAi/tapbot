/**
 * TapBot Catalog Backend - Types & DTOs
 */

export interface Env {
    DB: D1Database;
    BUCKET: R2Bucket;
    ADMIN_API_KEY?: string;
    ENVIRONMENT?: string;
}

// -------------------------------------------------------------
// Database Row Models (D1 SQLite)
// -------------------------------------------------------------

export interface CategoryRow {
    id: string;
    name: string;
    slug: string;
}

export interface BotRow {
    id: string;
    slug: string;
    name: string;
    description: string;
    long_description: string | null;
    icon_url: string | null;
    category: string;
    runtime: string;
    current_version: string | null;
    status: 'draft' | 'published' | 'archived';
    created_at: string;
    updated_at: string;
}

export interface BotVersionRow {
    id: string;
    bot_id: string;
    version: string;
    package_key: string;
    package_size: number;
    sha256: string;
    release_notes: string | null;
    minimum_app_version: number;
    published_at: string;
}

export interface BotCredentialRow {
    id: string;
    bot_id: string;
    key: string;
    display_name: string;
    description: string | null;
    required: number; // 0 or 1
    secret: number;   // 0 or 1
    input_type: string;
}

// -------------------------------------------------------------
// API Request & Response DTOs
// -------------------------------------------------------------

export interface ApiResponse<T = any> {
    success: boolean;
    data?: T;
    error?: {
        code: string;
        message: string;
        details?: any;
    };
}

export interface BotCredentialDto {
    key: string;
    displayName: string;
    description: string | null;
    required: boolean;
    secret: boolean;
    inputType: string;
}

export interface BotVersionDto {
    id: string;
    version: string;
    packageKey: string;
    packageSize: number;
    sha256: string;
    releaseNotes: string | null;
    minimumAppVersion: number;
    publishedAt: string;
    downloadUrl?: string;
}

export interface BotSummaryDto {
    id: string;
    slug: string;
    name: string;
    description: string;
    longDescription: string | null;
    iconUrl: string | null;
    category: string;
    runtime: string;
    currentVersion: string | null;
    status: string;
    createdAt: string;
    updatedAt: string;
    credentials?: BotCredentialDto[];
    currentVersionInfo?: BotVersionDto | null;
}

export interface BotDetailDto extends BotSummaryDto {
    credentials: BotCredentialDto[];
    currentVersionInfo: BotVersionDto | null;
}

// -------------------------------------------------------------
// Admin Request DTOs
// -------------------------------------------------------------

export interface CreateBotRequest {
    slug: string;
    name: string;
    description: string;
    longDescription?: string;
    iconUrl?: string;
    category: string;
    runtime?: string;
}

export interface UpdateBotRequest {
    name?: string;
    description?: string;
    longDescription?: string;
    iconUrl?: string;
    category?: string;
    runtime?: string;
}

export interface CreateVersionRequest {
    version: string;
    packageKey: string;
    packageSize: number;
    sha256: string;
    releaseNotes?: string;
    minimumAppVersion?: number;
}

export interface CreateCredentialRequest {
    key: string;
    displayName: string;
    description?: string;
    required?: boolean;
    secret?: boolean;
    inputType?: string;
}
