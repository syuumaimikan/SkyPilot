-- Per-user settings (e.g. the translation provider). API keys inside are encrypted with the
-- server secret (AES-256-GCM) and never returned to clients.
CREATE TABLE IF NOT EXISTS user_settings (
    user_id     UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    settings    JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
