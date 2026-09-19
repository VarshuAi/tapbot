-- Cloudflare D1 Migration: 0001_initial_schema.sql
-- TapBot Bot Store Catalog Schema

-- 1. Categories Table
CREATE TABLE IF NOT EXISTS categories (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE
);

-- 2. Bots Table
CREATE TABLE IF NOT EXISTS bots (
    id TEXT PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    long_description TEXT,
    icon_url TEXT,
    category TEXT NOT NULL,
    runtime TEXT NOT NULL DEFAULT 'native_art',
    current_version TEXT,
    status TEXT NOT NULL DEFAULT 'draft',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

-- 3. Bot Versions Table
CREATE TABLE IF NOT EXISTS bot_versions (
    id TEXT PRIMARY KEY,
    bot_id TEXT NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    package_key TEXT NOT NULL,
    package_size INTEGER NOT NULL,
    sha256 TEXT NOT NULL,
    release_notes TEXT,
    minimum_app_version INTEGER NOT NULL DEFAULT 1,
    published_at TEXT NOT NULL
);

-- 4. Bot Credentials Table
CREATE TABLE IF NOT EXISTS bot_credentials (
    id TEXT PRIMARY KEY,
    bot_id TEXT NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    display_name TEXT NOT NULL,
    description TEXT,
    required INTEGER NOT NULL DEFAULT 1,
    secret INTEGER NOT NULL DEFAULT 1,
    input_type TEXT NOT NULL DEFAULT 'text'
);

-- Performance Indexes
CREATE INDEX IF NOT EXISTS idx_categories_slug ON categories(slug);
CREATE INDEX IF NOT EXISTS idx_bots_status ON bots(status);
CREATE INDEX IF NOT EXISTS idx_bots_category ON bots(category);
CREATE INDEX IF NOT EXISTS idx_bots_slug ON bots(slug);
CREATE INDEX IF NOT EXISTS idx_bot_versions_bot_id ON bot_versions(bot_id);
CREATE INDEX IF NOT EXISTS idx_bot_credentials_bot_id ON bot_credentials(bot_id);
