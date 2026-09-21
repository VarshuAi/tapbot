-- Cloudflare D1 Migration: 0004_admin_dashboard_and_audit.sql
-- Adds audit_logs table and indexes for admin dashboard & bot lifecycle

CREATE TABLE IF NOT EXISTS audit_logs (
    id TEXT PRIMARY KEY,
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    details TEXT,
    actor TEXT NOT NULL DEFAULT 'admin',
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_bots_status_created ON bots(status, created_at DESC);
