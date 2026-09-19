#!/usr/bin/env node
/**
 * CLI Runner for validate-bot
 */

import * as path from 'path';
import { validateBotDirectory } from './validator.js';

export { validateManifest, validateBotDirectory } from './validator.js';
export * from './types.js';

function printUsage() {
    console.log(`
Usage:
  npx @tapbot/validate-bot <path-to-bot-directory>

Example:
  node tools/validate-bot/dist/index.js ./sample-bots/music-controller-bot
`);
}

function runCli() {
    const args = process.argv.slice(2);
    if (args.length === 0 || args.includes('--help') || args.includes('-h')) {
        printUsage();
        process.exit(args.length === 0 ? 1 : 0);
    }

    const targetPath = path.resolve(process.cwd(), args[0]);
    console.log(`\n🔍 Validating TapBot package at: ${targetPath}\n`);

    const result = validateBotDirectory(targetPath);

    if (result.warnings.length > 0) {
        console.log('⚠️  Warnings:');
        result.warnings.forEach(w => console.log(`  - ${w}`));
        console.log('');
    }

    if (!result.valid) {
        console.error('❌ Validation FAILED with errors:');
        result.errors.forEach(e => console.error(`  - ${e}`));
        console.log('');
        process.exit(1);
    }

    const m = result.manifest!;
    console.log('✅ VALIDATION PASSED!');
    console.log(`   Bot ID:    ${m.id}`);
    console.log(`   Name:      ${m.name}`);
    console.log(`   Version:   ${m.version}`);
    console.log(`   Runtime:   ${m.runtime}`);
    console.log(`   Category:  ${m.category}`);
    console.log(`   Credentials defined: ${m.credentials.length}`);
    console.log(`   Permissions:         ${m.permissions.join(', ')}\n`);

    process.exit(0);
}

// Only execute CLI if run directly
const isDirectRun = process.argv[1] && (
    process.argv[1].endsWith('validate-bot/dist/index.js') ||
    process.argv[1].endsWith('validate-bot/src/index.ts') ||
    process.argv[1].includes('validate-bot')
);

if (isDirectRun) {
    runCli();
}
