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

export type BotLifecycleStatus = 'draft' | 'review' | 'published' | 'unpublished' | 'archived';

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
    status: BotLifecycleStatus;
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
    minimum_runtime_version: string;
    status: 'published' | 'unpublished';
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

export interface AuditLogRow {
    id: string;
    action: string;
    entity_type: string;
    entity_id: string;
    details: string | null;
    actor: string;
    created_at: string;
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
    minimumRuntimeVersion: string;
    status: 'published' | 'unpublished';
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
    versionsCount?: number;
}

export interface BotDetailDto extends BotSummaryDto {
    credentials: BotCredentialDto[];
    currentVersionInfo: BotVersionDto | null;
    versions?: BotVersionDto[];
}

export interface AuditLogDto {
    id: string;
    action: string;
    entityType: string;
    entityId: string;
    details: any;
    actor: string;
    createdAt: string;
}

export interface AdminStatsDto {
    totalBots: number;
    publishedBots: number;
    draftBots: number;
    reviewBots: number;
    unpublishedBots: number;
    archivedBots: number;
    totalVersions: number;
    downloads: string;
    installs: string;
    lifecycleBreakdown: {
        draft: number;
        review: number;
        published: number;
        unpublished: number;
        archived: number;
    };
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
    status?: BotLifecycleStatus | string;
}

export interface UpdateBotRequest {
    name?: string;
    slug?: string;
    description?: string;
    longDescription?: string;
    iconUrl?: string;
    category?: string;
    runtime?: string;
    status?: BotLifecycleStatus | string;
}

export interface SetLifecycleRequest {
    status: 'DRAFT' | 'REVIEW' | 'PUBLISHED' | 'UNPUBLISHED' | 'ARCHIVED' | 'draft' | 'review' | 'published' | 'unpublished' | 'archived';
    reason?: string;
}

export interface CreateVersionRequest {
    version: string;
    packageKey: string;
    packageSize: number;
    sha256: string;
    releaseNotes?: string;
    minimumAppVersion?: number;
    minimumRuntimeVersion?: string;
    status?: 'published' | 'unpublished';
}

export interface CreateCredentialRequest {
    key: string;
    displayName: string;
    description?: string;
    required?: boolean;
    secret?: boolean;
    inputType?: string;
}
