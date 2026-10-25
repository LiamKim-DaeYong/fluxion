package io.fluxion.marketdata.trade

import io.fluxion.TestcontainersConfiguration
import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant

// Checks the trade queries other modules rely on, against a real PostgreSQL.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["fluxion.ingestion.upbit.enabled=false"])
class TradeRepositoryIntegrationTests {
    @Autowired
    lateinit var trades: TradeRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("delete from trade")
    }

    @Test
    fun `returns the latest trade time of the market`() {
        // Inserted out of time order, so the newest row is not the last one inserted.
        trades.insertIfAbsent(trade("2", "KRW-BTC", at("08:00:02.456")))
        trades.insertIfAbsent(trade("1", "KRW-BTC", at("08:00:00.123")))
        trades.insertIfAbsent(trade("3", "KRW-ETH", at("08:00:05")))

        assertThat(trades.lastTradedAt("UPBIT", "KRW-BTC")).isEqualTo(at("08:00:02.456"))
    }

    @Test
    fun `returns null when the market has no stored trade`() {
        trades.insertIfAbsent(trade("1", "KRW-ETH", at("08:00:00")))

        assertThat(trades.lastTradedAt("UPBIT", "KRW-BTC")).isNull()
    }

    private fun trade(
        tradeId: String,
        symbol: String,
        tradedAt: Instant,
    ) = TradeEvent(
        venue = "UPBIT",
        symbol = symbol,
        tradeId = tradeId,
        price = BigDecimal("143250000.0"),
        quantity = BigDecimal("0.00012345"),
        side = TradeSide.BUY,
        tradedAt = tradedAt,
        receivedAt = tradedAt.plusMillis(300),
    )

    companion object {
        private fun at(time: String): Instant = Instant.parse("2026-10-25T${time}Z")
    }
}
