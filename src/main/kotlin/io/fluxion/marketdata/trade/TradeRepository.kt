package io.fluxion.marketdata.trade

import io.fluxion.ingestion.TradeEvent
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.ZoneOffset

@Repository
class TradeRepository(
    private val jdbcClient: JdbcClient,
) {
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
}
