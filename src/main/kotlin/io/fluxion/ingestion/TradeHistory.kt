package io.fluxion.ingestion

import java.time.Instant

// Trades already stored outside ingestion. Read at startup to backfill the gap left by a restart.
// Defined here and implemented by the storing module, so ingestion keeps no dependency on it.
interface TradeHistory {
    // Trade time of the latest stored trade, or null when none is stored.
    fun lastTradedAt(
        venue: String,
        symbol: String,
    ): Instant?
}
