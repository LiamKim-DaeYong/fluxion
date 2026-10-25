package io.fluxion.marketdata.trade

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeHistory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull

@Repository
class TradeRepository(
    private val jdbcClient: JdbcClient,
) : TradeHistory {
    // Stores the trade unless the same (venue, symbol, trade_id) is already stored.
    // Returns false for a duplicate, without failing the surrounding transaction.
    fun insertIfAbsent(trade: TradeEvent): Boolean =
        jdbcClient
            .sql(
                """
                INSERT INTO trade (venue, symbol, trade_id, price, quantity, side, traded_at, received_at)
                VALUES (:venue, :symbol, :tradeId, :price, :quantity, :side, :tradedAt, :receivedAt)
                ON CONFLICT (venue, symbol, trade_id) DO NOTHING
                """.trimIndent(),
            ).param("venue", trade.venue)
            .param("symbol", trade.symbol)
            .param("tradeId", trade.tradeId)
            .param("price", trade.price)
            .param("quantity", trade.quantity)
            .param("side", trade.side.name)
            // The PostgreSQL driver binds OffsetDateTime to timestamptz, but not Instant.
            .param("tradedAt", trade.tradedAt.atOffset(ZoneOffset.UTC))
            .param("receivedAt", trade.receivedAt.atOffset(ZoneOffset.UTC))
            .update() == 1

    // max() returns one row holding NULL when the market has no trade, which optional() maps to empty.
    override fun lastTradedAt(
        venue: String,
        symbol: String,
    ): Instant? =
        jdbcClient
            .sql("SELECT max(traded_at) FROM trade WHERE venue = :venue AND symbol = :symbol")
            .param("venue", venue)
            .param("symbol", symbol)
            .query(OffsetDateTime::class.java)
            .optional()
            .getOrNull()
            ?.toInstant()
}
