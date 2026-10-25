-- The restart backfill reads the latest trade time per market (#43), and time range queries will follow.
-- The unique (venue, symbol, trade_id) index cannot answer max(traded_at) without reading every row of the market.
CREATE INDEX trade_venue_symbol_traded_at_idx ON trade (venue, symbol, traded_at);
