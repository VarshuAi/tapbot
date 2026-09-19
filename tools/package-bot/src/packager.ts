/**
 * Bot Packager Implementation
 */

import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { validateBotDirectory, BotManifest } from '../../validate-bot/dist/index.js';
import { createZipArchive, ZipEntry } from './zip.js';

export interface PackageResult {
    success: boolean;
    manifest: BotManifest;
    packageFilename: string;
    packagePath: string;
    packageKey: string;
    packageSizeBytes: number;
    sha256Checksum: string;
    packageInfoPath: string;
}

function collectFiles(dir: string, baseDir: string = dir): ZipEntry[] {
    const entries: ZipEntry[] = [];
    const files = fs.readdirSync(dir);

    for (const file of files) {
        // Skip hidden, git, and build artifacts
        if (file.startsWith('.') || file === 'node_modules' || file.endsWith('.botpkg') || file === 'package-info.json') {
            continue;
        }

        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);

        if (stat.isDirectory()) {
            entries.push(...collectFiles(fullPath, baseDir));
        } else if (stat.isFile()) {
            const relativeName = path.relative(baseDir, fullPath).replace(/\\/g, '/');
            const data = fs.readFileSync(fullPath);
            entries.push({ name: relativeName, data });
        }
    }

    return entries;
}

export function packageBot(sourceDir: string, outputDir?: string): PackageResult {
    const resolvedSource = path.resolve(process.cwd(), sourceDir);
    const resolvedOutput = path.resolve(process.cwd(), outputDir || sourceDir);

    // 1. Validate
    const validation = validateBotDirectory(resolvedSource);
    if (!validation.valid || !validation.manifest) {
        throw new Error(`Cannot package bot. Validation failed:\n${validation.errors.map(e => `  - ${e}`).join('\n')}`);
    }

    const manifest = validation.manifest;

    // 2. Collect package files
    const entries = collectFiles(resolvedSource);
    if (entries.length === 0) {
        throw new Error(`Source directory has no packageable files: ${resolvedSource}`);
    }

    // 3. Create standard ZIP archive
    const zipBuffer = createZipArchive(entries);

    // 4. Compute integrity hashes
    const sha256 = crypto.createHash('sha256').update(zipBuffer).digest('hex');
    const sizeBytes = zipBuffer.length;

    // 5. Write .botpkg
    if (!fs.existsSync(resolvedOutput)) {
        fs.mkdirSync(resolvedOutput, { recursive: true });
    }

    const packageFilename = `${manifest.id}-${manifest.version}.botpkg`;
    const packageKey = `packages/${manifest.id}_${manifest.version}.botpkg`;
    const packagePath = path.join(resolvedOutput, packageFilename);

    fs.writeFileSync(packagePath, zipBuffer);

    // 6. Write package-info.json metadata
    const packageInfo = {
        id: manifest.id,
        name: manifest.name,
        version: manifest.version,
        runtime: manifest.runtime,
        category: manifest.category,
        packageKey,
        packageFilename,
        packagePath,
        packageSizeBytes: sizeBytes,
        sha256Checksum: sha256,
        minimumAppVersion: manifest.minimumAppVersion,
        minimumRuntimeVersion: manifest.minimumRuntimeVersion,
        releaseNotes: `Package build for ${manifest.name} v${manifest.version}`,
        builtAt: new Date().toISOString()
    };

    const packageInfoPath = path.join(resolvedOutput, 'package-info.json');
    fs.writeFileSync(packageInfoPath, JSON.stringify(packageInfo, null, 2), 'utf-8');

    return {
        success: true,
        manifest,
        packageFilename,
        packagePath,
        packageKey,
        packageSizeBytes: sizeBytes,
        sha256Checksum: sha256,
        packageInfoPath
    };
}
