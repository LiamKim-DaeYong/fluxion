package io.fluxion.marketdata.trade

import io.fluxion.TestcontainersConfiguration
import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

// Publishes TradeEvents the same way the ingestion module does and checks the trade table.
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["fluxion.ingestion.upbit.enabled=false"])
class TradeRecorderIntegrationTests {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("delete from trade")
    }

    @Test
    fun `stores a published trade`() {
        val trade = trade("1")

        publish(trade)

        // untilAsserted retries only on AssertionError, so check the row count with an assertion
        // instead of queryForMap, which throws EmptyResultDataAccessException before the listener runs.
        await().atMost(TIMEOUT).untilAsserted {
            val rows = jdbcTemplate.queryForList("select * from trade where trade_id = '1'")
            assertThat(rows).hasSize(1)
            val row = rows.single()
            assertThat(row["venue"]).isEqualTo("UPBIT")
            assertThat(row["symbol"]).isEqualTo("KRW-BTC")
            assertThat(row["price"] as BigDecimal).isEqualByComparingTo(trade.price)
            assertThat(row["quantity"] as BigDecimal).isEqualByComparingTo(trade.quantity)
            assertThat(row["side"]).isEqualTo("BUY")
            assertThat((row["traded_at"] as Timestamp).toInstant()).isEqualTo(trade.tradedAt)
            assertThat((row["received_at"] as Timestamp).toInstant()).isEqualTo(trade.receivedAt)
        }
    }

    @Test
    fun `stores a trade only once when it arrives twice`() {
        publish(trade("2"))
        publish(trade("2"))

        // Both listener runs must complete; a failed duplicate insert would leave a publication incomplete.
        await().atMost(TIMEOUT).untilAsserted {
            assertThat(incompleteTradePublications()).isZero()
            assertThat(countTrades("2")).isEqualTo(1)
        }
    }

    private fun publish(trade: TradeEvent) {
        transactionTemplate.executeWithoutResult { events.publishEvent(trade) }
    }

    private fun countTrades(tradeId: String): Int =
        jdbcTemplate.queryForObject("select count(*) from trade where trade_id = ?", Int::class.java, tradeId)!!

    private fun incompleteTradePublications(): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from event_publication where event_type = ? and completion_date is null",
            Int::class.java,
            TradeEvent::class.java.name,
        )!!

    private fun trade(tradeId: String) =
        TradeEvent(
            venue = "UPBIT",
            symbol = "KRW-BTC",
            tradeId = tradeId,
            price = BigDecimal("143250000.0"),
            quantity = BigDecimal("0.00012345"),
            side = TradeSide.BUY,
            tradedAt = Instant.parse("2026-10-25T08:00:00.123Z"),
            receivedAt = Instant.parse("2026-10-25T08:00:00.456Z"),
        )

    companion object {
        private val TIMEOUT = Duration.ofSeconds(10)
    }
}
