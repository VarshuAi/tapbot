/**
 * Bot Package Validator Logic
 */

import * as fs from 'fs';
import * as path from 'path';
import {
    BotManifest,
    ValidationResult,
    SUPPORTED_RUNTIMES,
    SUPPORTED_CATEGORIES,
    ENFORCEABLE_PERMISSIONS,
    VALID_INPUT_TYPES
} from './types.js';

const SEMVER_REGEX = /^([0-9]+)\.([0-9]+)\.([0-9]+)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/;
const ID_REGEX = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const KEY_REGEX = /^[a-z0-9_]+$/;

export function validateManifest(raw: any): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
        return {
            valid: false,
            errors: ['Manifest must be a valid JSON object.'],
            warnings: []
        };
    }

    // 1. Bot ID
    if (!raw.id || typeof raw.id !== 'string') {
        errors.push("Missing or invalid 'id'. Must be a string.");
    } else if (!ID_REGEX.test(raw.id)) {
        errors.push(`Invalid 'id' format ('${raw.id}'). Must be lowercase alphanumeric with hyphens (e.g. 'music-controller-bot').`);
    }

    // 2. Name
    if (!raw.name || typeof raw.name !== 'string') {
        errors.push("Missing or invalid 'name'. Must be a string.");
    } else if (raw.name.length < 3 || raw.name.length > 64) {
        errors.push(`Bot name must be between 3 and 64 characters (current: ${raw.name.length}).`);
    }

    // 3. Version
    if (!raw.version || typeof raw.version !== 'string') {
        errors.push("Missing or invalid 'version'. Must be a string.");
    } else if (!SEMVER_REGEX.test(raw.version)) {
        errors.push(`Version '${raw.version}' is not valid semantic versioning (expected format: X.Y.Z).`);
    }

    // 4. Description
    if (!raw.description || typeof raw.description !== 'string') {
        errors.push("Missing or invalid 'description'. Must be a string.");
    } else if (raw.description.length < 10 || raw.description.length > 256) {
        errors.push(`Description must be between 10 and 256 characters (current: ${raw.description.length}).`);
    }

    // 5. Category
    if (!raw.category || typeof raw.category !== 'string') {
        errors.push("Missing or invalid 'category'. Must be a string.");
    } else if (!SUPPORTED_CATEGORIES.includes(raw.category as any)) {
        errors.push(`Unsupported category '${raw.category}'. Allowed: ${SUPPORTED_CATEGORIES.join(', ')}.`);
    }

    // 6. Runtime
    if (!raw.runtime || typeof raw.runtime !== 'string') {
        errors.push("Missing or invalid 'runtime'. Must be a string.");
    } else if (!SUPPORTED_RUNTIMES.includes(raw.runtime as any)) {
        errors.push(`Unsupported runtime '${raw.runtime}'. Allowed: ${SUPPORTED_RUNTIMES.join(', ')}.`);
    }

    // 7. Entrypoint
    if (!raw.entrypoint || typeof raw.entrypoint !== 'string' || raw.entrypoint.trim().length === 0) {
        errors.push("Missing or empty 'entrypoint'. Must specify a valid class identifier or entrypoint file.");
    }

    // 8. Minimum Versions
    if (!raw.minimumRuntimeVersion || typeof raw.minimumRuntimeVersion !== 'string') {
        errors.push("Missing or invalid 'minimumRuntimeVersion'. Must be a semver string.");
    } else if (!SEMVER_REGEX.test(raw.minimumRuntimeVersion)) {
        errors.push(`minimumRuntimeVersion '${raw.minimumRuntimeVersion}' is not valid semver.`);
    }

    if (raw.minimumAppVersion === undefined || typeof raw.minimumAppVersion !== 'number' || !Number.isInteger(raw.minimumAppVersion) || raw.minimumAppVersion < 1) {
        errors.push("Missing or invalid 'minimumAppVersion'. Must be an integer >= 1.");
    }

    // 9. Icon
    if (!raw.icon || typeof raw.icon !== 'string') {
        errors.push("Missing or invalid 'icon'. Must specify path to icon asset (e.g. 'assets/icon.png').");
    }

    // 10. Credentials Schema
    if (!Array.isArray(raw.credentials)) {
        errors.push("Missing 'credentials' array. Must declare credential requirements.");
    } else {
        const seenKeys = new Set<string>();
        let hasBotToken = false;

        raw.credentials.forEach((cred: any, index: number) => {
            if (!cred || typeof cred !== 'object') {
                errors.push(`credentials[${index}] must be an object.`);
                return;
            }

            if (!cred.key || typeof cred.key !== 'string') {
                errors.push(`credentials[${index}] missing required 'key'.`);
            } else if (!KEY_REGEX.test(cred.key)) {
                errors.push(`credentials[${index}].key '${cred.key}' is invalid. Must be lowercase alphanumeric/underscore.`);
            } else if (seenKeys.has(cred.key)) {
                errors.push(`Duplicate credential key '${cred.key}' in credentials.`);
            } else {
                seenKeys.add(cred.key);
                if (cred.key === 'bot_token') hasBotToken = true;
            }

            if (!cred.label || typeof cred.label !== 'string') {
                errors.push(`credentials[${index}] missing required 'label'.`);
            }

            if (typeof cred.required !== 'boolean') {
                errors.push(`credentials[${index}].required must be boolean.`);
            }

            if (typeof cred.secret !== 'boolean') {
                errors.push(`credentials[${index}].secret must be boolean.`);
            }

            if (cred.inputType && !VALID_INPUT_TYPES.includes(cred.inputType)) {
                errors.push(`credentials[${index}].inputType '${cred.inputType}' is invalid. Allowed: ${VALID_INPUT_TYPES.join(', ')}.`);
            }
        });

        if (!hasBotToken) {
            warnings.push("Package does not define 'bot_token' in credentials. Telegram bots normally require a Telegram bot token.");
        }
    }

    // 11. Permissions
    if (!Array.isArray(raw.permissions)) {
        errors.push("Missing 'permissions' array. Must declare requested platform permissions.");
    } else {
        raw.permissions.forEach((perm: any) => {
            if (typeof perm !== 'string' || !ENFORCEABLE_PERMISSIONS.includes(perm as any)) {
                errors.push(`Unauthorized or non-enforceable permission '${perm}'. The runtime strictly permits only: ${ENFORCEABLE_PERMISSIONS.join(', ')}.`);
            }
        });
    }

    return {
        valid: errors.length === 0,
        errors,
        warnings,
        manifest: errors.length === 0 ? (raw as BotManifest) : undefined
    };
}

export function validateBotDirectory(dirPath: string): ValidationResult {
    const errors: string[] = [];
    const warnings: string[] = [];

    // Check directory existence
    if (!fs.existsSync(dirPath)) {
        return {
            valid: false,
            errors: [`Package directory does not exist: ${dirPath}`],
            warnings: []
        };
    }

    const stat = fs.statSync(dirPath);
    if (!stat.isDirectory()) {
        return {
            valid: false,
            errors: [`Specified path is not a directory: ${dirPath}`],
            warnings: []
        };
    }

    // Check manifest.json
    const manifestPath = path.join(dirPath, 'manifest.json');
    if (!fs.existsSync(manifestPath)) {
        return {
            valid: false,
            errors: [`Missing required 'manifest.json' in ${dirPath}`],
            warnings: []
        };
    }

    let rawJson: any;
    try {
        const fileContent = fs.readFileSync(manifestPath, 'utf-8');
        rawJson = JSON.parse(fileContent);
    } catch (e: any) {
        return {
            valid: false,
            errors: [`Failed to parse 'manifest.json' as JSON: ${e.message}`],
            warnings: []
        };
    }

    // Run schema validation
    const schemaResult = validateManifest(rawJson);
    errors.push(...schemaResult.errors);
    warnings.push(...schemaResult.warnings);

    if (schemaResult.manifest) {
        const manifest = schemaResult.manifest;

        // Verify icon file exists
        const iconPath = path.join(dirPath, manifest.icon);
        if (!fs.existsSync(iconPath)) {
            errors.push(`Declared icon file does not exist: ${manifest.icon}`);
        }

        // Verify assets directory
        const assetsDir = path.join(dirPath, 'assets');
        if (!fs.existsSync(assetsDir)) {
            warnings.push("Package directory missing 'assets/' folder.");
        }

        // Verify entrypoint (class identifier or existing file)
        const entrypointPath = path.join(dirPath, manifest.entrypoint);
        const entrypointFile = path.join(dirPath, 'entrypoint');
        if (!manifest.entrypoint.includes('.') && !fs.existsSync(entrypointPath) && !fs.existsSync(entrypointFile)) {
            warnings.push(`Declared entrypoint '${manifest.entrypoint}' is not a package file or qualified class.`);
        }
    }

    return {
        valid: errors.length === 0,
        errors,
        warnings,
        manifest: schemaResult.manifest
    };
}
