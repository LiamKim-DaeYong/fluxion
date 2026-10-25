package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
import java.time.Instant

class UpbitTradeTickTests {
    // Spring Boot registers the Kotlin module automatically; a plain unit test has to add it.
    private val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `converts a recent trades response to trade events`() {
        // Captured from GET /v1/trades/ticks?market=KRW-BTC on 2026-10-25.
        val json =
            """
            [{"market":"KRW-BTC","trade_date_utc":"2026-10-25","trade_time_utc":"09:35:26","timestamp":1792920926054,
            "trade_price":107680000.00000000,"trade_volume":0.00031335,"prev_closing_price":108517000.00000000,
            "change_price":-837000.00000000,"ask_bid":"BID","sequential_id":17929209260540000}]
            """.trimIndent()
        val receivedAt = Instant.parse("2026-10-25T09:35:30Z")

        val ticks = jsonMapper.readValue(json, Array<UpbitTradeTick>::class.java)

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
