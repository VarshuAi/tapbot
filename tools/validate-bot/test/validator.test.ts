import { describe, it, expect } from 'vitest';
import { validateManifest } from '../src/validator.js';

describe('Bot Package Validator', () => {
    const validManifest = {
        id: 'music-controller-bot',
        name: 'Music Controller Bot',
        version: '1.0.0',
        description: 'Control media playback via Telegram.',
        category: 'media',
        icon: 'assets/icon.png',
        runtime: 'native_art',
        entrypoint: 'com.tapbot.bots.music.MusicBotRunner',
        minimumRuntimeVersion: '1.0.0',
        minimumAppVersion: 1,
        credentials: [
            {
                key: 'bot_token',
                label: 'Telegram Bot Token',
                required: true,
                secret: true,
                inputType: 'password'
            },
            {
                key: 'spotify_client_id',
                label: 'Spotify Client ID',
                required: true,
                secret: false,
                inputType: 'text'
            }
        ],
        permissions: ['INTERNET', 'FOREGROUND_SERVICE', 'NOTIFICATIONS']
    };

    it('passes for a valid manifest', () => {
        const result = validateManifest(validManifest);
        expect(result.valid).toBe(true);
        expect(result.errors).toHaveLength(0);
        expect(result.manifest?.name).toBe('Music Controller Bot');
    });

    it('rejects invalid bot id', () => {
        const result = validateManifest({ ...validManifest, id: 'Music_Bot!' });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes("Invalid 'id' format"))).toBe(true);
    });

    it('rejects invalid semver version', () => {
        const result = validateManifest({ ...validManifest, version: 'v1' });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes('not valid semantic versioning'))).toBe(true);
    });

    it('rejects unsupported runtime', () => {
        const result = validateManifest({ ...validManifest, runtime: 'python_cpython' });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes('Unsupported runtime'))).toBe(true);
    });

    it('rejects phantom/unsupported permissions', () => {
        const result = validateManifest({
            ...validManifest,
            permissions: ['INTERNET', 'ROOT', 'READ_SMS']
        });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes("Unauthorized or non-enforceable permission 'ROOT'"))).toBe(true);
    });

    it('rejects invalid credential input type', () => {
        const result = validateManifest({
            ...validManifest,
            credentials: [
                {
                    key: 'bot_token',
                    label: 'Token',
                    required: true,
                    secret: true,
                    inputType: 'invalid_type'
                }
            ]
        });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes('inputType'))).toBe(true);
    });

    it('rejects duplicate credential keys', () => {
        const result = validateManifest({
            ...validManifest,
            credentials: [
                { key: 'bot_token', label: 'Token 1', required: true, secret: true },
                { key: 'bot_token', label: 'Token 2', required: true, secret: true }
            ]
        });
        expect(result.valid).toBe(false);
        expect(result.errors.some(e => e.includes("Duplicate credential key 'bot_token'"))).toBe(true);
    });
});
