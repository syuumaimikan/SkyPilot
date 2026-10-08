CREATE TABLE market_orders (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source TEXT NOT NULL CHECK (source IN ('bazaar', 'ah')),
    snapshot JSONB NOT NULL,
    PRIMARY KEY (user_id, source)
);
