-- Cloudflare D1 Migration: 0003_version_management.sql
-- Adds status (published/unpublished) and minimum_runtime_version to bot_versions

ALTER TABLE bot_versions ADD COLUMN status TEXT NOT NULL DEFAULT 'published';
ALTER TABLE bot_versions ADD COLUMN minimum_runtime_version TEXT NOT NULL DEFAULT '1.0.0';
CREATE INDEX IF NOT EXISTS idx_bot_versions_status ON bot_versions(status);
