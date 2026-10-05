-- SkyPilot initial schema. Applied by `skypilot-api migrate` (sqlx migrate) on startup.

CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    login           TEXT UNIQUE,
    password_hash   TEXT,
    minecraft_uuid  TEXT,
    minecraft_name  TEXT,
    profile_id      TEXT,
    play_style      TEXT NOT NULL DEFAULT 'FASTEST',
    budget          DOUBLE PRECISION,
    lang            TEXT NOT NULL DEFAULT 'ja',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS web_sessions (
    id_hash     TEXT PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    csrf_token  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS web_sessions_user ON web_sessions(user_id);

CREATE TABLE IF NOT EXISTS minecraft_accounts (
    uuid        TEXT PRIMARY KEY,
    username    TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS skyblock_profiles (
    profile_id  TEXT PRIMARY KEY,
    cute_name   TEXT NOT NULL,
    game_mode   TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per user-initiated analysis (never polled in the background; Hypixel API policy).
CREATE TABLE IF NOT EXISTS profile_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    minecraft_uuid  TEXT NOT NULL,
    profile_id      TEXT NOT NULL,
    taken_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    metrics         JSONB NOT NULL,
    analysis        JSONB
);
CREATE INDEX IF NOT EXISTS profile_snapshots_player ON profile_snapshots(minecraft_uuid, profile_id, taken_at DESC);
CREATE INDEX IF NOT EXISTS profile_snapshots_user ON profile_snapshots(user_id, taken_at DESC);

-- Latest raw Hypixel responses for a user's analysed profile (gzip JSON), so advisor pages
-- can be recomputed without calling Hypixel again. One row per user.
CREATE TABLE IF NOT EXISTS raw_profiles (
    user_id         UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    minecraft_uuid  TEXT NOT NULL,
    username        TEXT NOT NULL,
    profile_id      TEXT,
    fetched_at      TIMESTAMPTZ NOT NULL,
    profiles        BYTEA NOT NULL,
    museum          BYTEA,
    garden          BYTEA
);

CREATE TABLE IF NOT EXISTS goals (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id          TEXT NOT NULL,
    text        TEXT NOT NULL,
    priority    TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS goal_nodes (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_id     TEXT NOT NULL,
    node_id     TEXT NOT NULL,
    payload     JSONB NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, goal_id, node_id)
);

CREATE TABLE IF NOT EXISTS roadmaps (
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_id           TEXT NOT NULL,
    title             TEXT NOT NULL,
    overall_progress  DOUBLE PRECISION NOT NULL,
    payload           JSONB NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, goal_id)
);

CREATE TABLE IF NOT EXISTS recommendations (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    actions     JSONB NOT NULL
);

-- Per-domain extracts of each analysis (for progress charts / history).
CREATE TABLE IF NOT EXISTS skills (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL,
    skill TEXT NOT NULL, level INT NOT NULL, xp DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at, skill)
);
CREATE TABLE IF NOT EXISTS mining_profiles (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL,
    hotm INT, mithril_powder DOUBLE PRECISION, gemstone_powder DOUBLE PRECISION, glacite_powder DOUBLE PRECISION, data JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at)
);
CREATE TABLE IF NOT EXISTS hotm_snapshots (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL, nodes JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at)
);
CREATE TABLE IF NOT EXISTS slayer_profiles (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL,
    slayer TEXT NOT NULL, level INT NOT NULL, xp DOUBLE PRECISION NOT NULL, kills JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at, slayer)
);
CREATE TABLE IF NOT EXISTS garden_profiles (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL, garden_level INT, data JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at)
);
CREATE TABLE IF NOT EXISTS dungeon_profiles (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL,
    catacombs DOUBLE PRECISION, class_average DOUBLE PRECISION, data JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at)
);
CREATE TABLE IF NOT EXISTS accessory_snapshots (
    minecraft_uuid TEXT NOT NULL, profile_id TEXT NOT NULL, taken_at TIMESTAMPTZ NOT NULL,
    magical_power INT, accessories JSONB NOT NULL,
    PRIMARY KEY (minecraft_uuid, profile_id, taken_at)
);

-- Market
CREATE TABLE IF NOT EXISTS items (
    id              TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    tier            TEXT,
    category        TEXT,
    npc_sell_price  DOUBLE PRECISION,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS item_variants (
    price_key   TEXT PRIMARY KEY,
    item_id     TEXT NOT NULL,
    first_seen  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS auctions (
    auction_id  TEXT PRIMARY KEY,
    item_id     TEXT NOT NULL,
    price_key   TEXT NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    bin         BOOLEAN NOT NULL,
    start_ms    BIGINT NOT NULL,
    end_ms      BIGINT NOT NULL,
    seen_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS auction_sales (
    auction_id  TEXT NOT NULL,
    item_id     TEXT NOT NULL,
    price_key   TEXT NOT NULL,
    unit_price  DOUBLE PRECISION NOT NULL,
    count       INT NOT NULL,
    bin         BOOLEAN NOT NULL,
    sold_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (auction_id, sold_at)
);
CREATE INDEX IF NOT EXISTS auction_sales_key ON auction_sales(price_key, sold_at DESC);
CREATE INDEX IF NOT EXISTS auction_sales_item ON auction_sales(item_id, sold_at DESC);

CREATE TABLE IF NOT EXISTS bazaar_snapshots (
    product_id       TEXT NOT NULL,
    ts               TIMESTAMPTZ NOT NULL,
    buy              DOUBLE PRECISION,
    sell             DOUBLE PRECISION,
    buy_volume       DOUBLE PRECISION,
    sell_volume      DOUBLE PRECISION,
    buy_moving_week  DOUBLE PRECISION,
    sell_moving_week DOUBLE PRECISION,
    buy_orders       INT,
    sell_orders      INT,
    PRIMARY KEY (product_id, ts)
);

CREATE TABLE IF NOT EXISTS price_history (
    item_id  TEXT NOT NULL,
    bucket   TIMESTAMPTZ NOT NULL,
    median   DOUBLE PRECISION,
    low      DOUBLE PRECISION,
    high     DOUBLE PRECISION,
    volume   DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (item_id, bucket)
);

CREATE TABLE IF NOT EXISTS flip_candidates (
    id                BIGSERIAL,
    ts                TIMESTAMPTZ NOT NULL,
    price_key         TEXT NOT NULL,
    item_id           TEXT NOT NULL,
    buy_price         DOUBLE PRECISION NOT NULL,
    recommended_sell  DOUBLE PRECISION NOT NULL,
    fair_value        DOUBLE PRECISION NOT NULL,
    roi               DOUBLE PRECISION NOT NULL,
    expected_profit   DOUBLE PRECISION NOT NULL,
    sales_per_day     DOUBLE PRECISION NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (id, ts)
);
CREATE INDEX IF NOT EXISTS flip_candidates_ts ON flip_candidates(ts DESC);

-- Mod / device authorization (RFC 8628 style). Only hashes of codes/tokens are stored.
CREATE TABLE IF NOT EXISTS device_codes (
    device_code_hash  TEXT PRIMARY KEY,
    user_code         TEXT UNIQUE NOT NULL,
    device_name       TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,
    approved_user_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    denied            BOOLEAN NOT NULL DEFAULT false,
    last_poll_at      TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS device_tokens (
    id            TEXT PRIMARY KEY,
    token_hash    TEXT UNIQUE NOT NULL,
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ,
    revoked       BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS notification_rules (
    user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    id       TEXT NOT NULL,
    rule     JSONB NOT NULL,
    PRIMARY KEY (user_id, id)
);
CREATE TABLE IF NOT EXISTS push_subscriptions (
    endpoint    TEXT PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS session_stats (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    device_id   TEXT,
    started_at  TIMESTAMPTZ NOT NULL,
    ended_at    TIMESTAMPTZ NOT NULL,
    stats       JSONB NOT NULL
);
