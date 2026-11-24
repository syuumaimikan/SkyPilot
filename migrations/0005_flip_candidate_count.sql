-- Flip calibration: stack size of the candidate (recommended_sell is for the whole stack) and
-- the model's probability of sale when it was seen. Old rows count as single items.
ALTER TABLE flip_candidates ADD COLUMN IF NOT EXISTS item_count INTEGER NOT NULL DEFAULT 1;
ALTER TABLE flip_candidates ADD COLUMN IF NOT EXISTS p_sale DOUBLE PRECISION;
