#!/usr/bin/env node
"use strict";
/**
 * CLI Runner for validate-bot
 */
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __exportStar = (this && this.__exportStar) || function(m, exports) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports, p)) __createBinding(exports, m, p);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.validateBotDirectory = exports.validateManifest = void 0;
const path = __importStar(require("path"));
const validator_js_1 = require("./validator.js");
var validator_js_2 = require("./validator.js");
Object.defineProperty(exports, "validateManifest", { enumerable: true, get: function () { return validator_js_2.validateManifest; } });
Object.defineProperty(exports, "validateBotDirectory", { enumerable: true, get: function () { return validator_js_2.validateBotDirectory; } });
__exportStar(require("./types.js"), exports);
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
    const result = (0, validator_js_1.validateBotDirectory)(targetPath);
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
    const m = result.manifest;
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
const isDirectRun = process.argv[1] && (process.argv[1].endsWith('validate-bot/dist/index.js') ||
    process.argv[1].endsWith('validate-bot/src/index.ts') ||
    process.argv[1].includes('validate-bot'));
if (isDirectRun) {
    runCli();
}
