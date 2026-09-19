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
export declare const SUPPORTED_RUNTIMES: readonly ["native_art"];
export declare const SUPPORTED_CATEGORIES: readonly ["utilities", "productivity", "media", "automation"];
export declare const ENFORCEABLE_PERMISSIONS: readonly ["INTERNET", "FOREGROUND_SERVICE", "NOTIFICATIONS"];
export declare const VALID_INPUT_TYPES: readonly ["text", "password", "number", "url"];
