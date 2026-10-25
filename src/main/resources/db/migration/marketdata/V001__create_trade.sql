-- Trades received from external venues (#35).
-- The same trade can arrive twice (reconnect snapshot, backfill), so (venue, symbol, trade_id) is unique.
CREATE TABLE trade
(
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  venue       TEXT                     NOT NULL,
  symbol      TEXT                     NOT NULL,
  trade_id    TEXT                     NOT NULL,
  price       NUMERIC                  NOT NULL,
  quantity    NUMERIC                  NOT NULL,
  side        TEXT                     NOT NULL,
  traded_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  received_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT trade_side_check CHECK (side IN ('BUY', 'SELL')),
  CONSTRAINT trade_venue_symbol_trade_id_key UNIQUE (venue, symbol, trade_id)
);
