package io.fluxion.ingestion.upbit.backfill

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import io.fluxion.ingestion.upbit.UpbitFixtures.CAPTURED_TICKS
import io.fluxion.ingestion.upbit.UpbitFixtures.jsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class UpbitTradeTickTests {
    @Test
    fun `converts a recent trades response to trade events`() {
        val receivedAt = Instant.parse("2026-10-25T09:35:30Z")

        val ticks = jsonMapper.readValue(CAPTURED_TICKS, Array<UpbitTradeTick>::class.java)

        assertThat(ticks.map { it.toEvent(receivedAt) }).containsExactly(
            TradeEvent(
                venue = "UPBIT",
                symbol = "KRW-BTC",
                tradeId = "17929209260540000",
                price = BigDecimal("107680000.00000000"),
                quantity = BigDecimal("0.00031335"),
                side = TradeSide.BUY,
                tradedAt = Instant.ofEpochMilli(1792920926054),
                receivedAt = receivedAt,
            ),
        )
    }
}
