#!/usr/bin/env node
/**
 * CLI Runner for package-bot
 */

import * as path from 'path';
import { packageBot } from './packager.js';

export { packageBot } from './packager.js';
export * from './zip.js';

function printUsage() {
    console.log(`
Usage:
  npx @tapbot/package-bot <path-to-bot-dir> [output-directory]

Example:
  node tools/package-bot/dist/index.js ./sample-bots/music-controller-bot ./dist
`);
}

function runCli() {
    const args = process.argv.slice(2);
    if (args.length === 0 || args.includes('--help') || args.includes('-h')) {
        printUsage();
        process.exit(args.length === 0 ? 1 : 0);
    }

    const sourceDir = args[0];
    const outputDir = args[1] || sourceDir;

    console.log(`\n📦 Packaging TapBot package from: ${sourceDir}`);
    console.log(`   Output Directory:               ${outputDir}\n`);

    try {
        const result = packageBot(sourceDir, outputDir);

        console.log('✅ BOT PACKAGE BUILT SUCCESSFULLY!');
        console.log(`   Bot:         ${result.manifest.name} (${result.manifest.id})`);
        console.log(`   Version:     ${result.manifest.version}`);
        console.log(`   Archive:     ${result.packageFilename}`);
        console.log(`   Size:        ${result.packageSizeBytes} bytes`);
        console.log(`   SHA-256:     ${result.sha256Checksum}`);
        console.log(`   Package Key: ${result.packageKey}`);
        console.log(`   Metadata:    ${result.packageInfoPath}\n`);

        process.exit(0);
    } catch (e: any) {
        console.error(`\n❌ Error packaging bot: ${e.message}\n`);
        process.exit(1);
    }
}

const isDirectRun = process.argv[1] && (
    process.argv[1].endsWith('package-bot/dist/index.js') ||
    process.argv[1].endsWith('package-bot/src/index.ts') ||
    process.argv[1].includes('package-bot')
);

if (isDirectRun) {
    runCli();
}
