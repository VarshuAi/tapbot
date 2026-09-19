-- Cloudflare D1 Schema for TapBot Catalog

CREATE TABLE IF NOT EXISTS bots (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    summary TEXT NOT NULL,
    description TEXT NOT NULL,
    author TEXT NOT NULL,
    version TEXT NOT NULL,
    icon_url TEXT NOT NULL,
    category TEXT NOT NULL,
    tags TEXT NOT NULL, -- JSON array of strings
    required_credentials TEXT NOT NULL, -- JSON array of BotCredentialSpec objects
    package_url TEXT NOT NULL,
    package_sha256 TEXT NOT NULL,
    package_size_bytes INTEGER NOT NULL DEFAULT 0,
    runtime_type TEXT NOT NULL DEFAULT 'built-in', -- 'built-in', 'dex'
    entry_class TEXT,
    permissions TEXT NOT NULL DEFAULT '[]', -- JSON array of strings
    repository_url TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_bots_category ON bots (category);
CREATE INDEX IF NOT EXISTS idx_bots_created_at ON bots (created_at DESC);
