/**
 * TapBot Bot Package Manifest Types
 */

export interface BotManifest {
    $schema?: string;
    id: string;
    name: string;
    version: string;
    description: string;
    longDescription?: string;
    category: string;
    icon: string;
    runtime: string;
    entrypoint: string;
    minimumRuntimeVersion: string;
    minimumAppVersion: number;
    credentials: CredentialSpec[];
    permissions: string[];
    packageMetadata?: {
        author?: string;
        license?: string;
        repository?: string;
        createdAt?: string;
    };
}

export interface CredentialSpec {
    key: string;
    label: string;
    description?: string;
    required: boolean;
    secret: boolean;
    inputType?: 'text' | 'password' | 'number' | 'url';
    placeholder?: string;
    helpUrl?: string;
}

export interface ValidationResult {
    valid: boolean;
    errors: string[];
    warnings: string[];
    manifest?: BotManifest;
}

export const SUPPORTED_RUNTIMES = ['native_art'] as const;
export const SUPPORTED_CATEGORIES = ['utilities', 'productivity', 'media', 'automation'] as const;
export const ENFORCEABLE_PERMISSIONS = ['INTERNET', 'FOREGROUND_SERVICE', 'NOTIFICATIONS'] as const;
export const VALID_INPUT_TYPES = ['text', 'password', 'number', 'url'] as const;
