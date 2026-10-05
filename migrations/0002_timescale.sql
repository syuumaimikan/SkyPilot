-- Turn the market time-series tables into TimescaleDB hypertables when the extension is
-- available (docker-compose uses timescale/timescaledb). On plain PostgreSQL this is a no-op
-- and the tables keep working as regular indexed tables.
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS timescaledb;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'timescaledb not available - using plain tables';
        RETURN;
    END;
    PERFORM create_hypertable('auction_sales', 'sold_at', if_not_exists => TRUE, migrate_data => TRUE);
    PERFORM create_hypertable('bazaar_snapshots', 'ts', if_not_exists => TRUE, migrate_data => TRUE);
    PERFORM create_hypertable('flip_candidates', 'ts', if_not_exists => TRUE, migrate_data => TRUE);
    PERFORM create_hypertable('price_history', 'bucket', if_not_exists => TRUE, migrate_data => TRUE);
    -- keep raw market data bounded
    BEGIN
        PERFORM add_retention_policy('bazaar_snapshots', INTERVAL '120 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('flip_candidates', INTERVAL '60 days', if_not_exists => TRUE);
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'retention policies unavailable (community edition required)';
    END;
END
$$;
