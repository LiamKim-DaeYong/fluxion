package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.TradeSide
import io.fluxion.ingestion.upbit.UpbitFixtures.counterValue
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionOperations
import java.math.BigDecimal
import java.time.Instant

class UpbitTradePublisherTests {
    private val meterRegistry = SimpleMeterRegistry()

    private val event =
        TradeEvent(
            venue = Upbit.VENUE,
            symbol = "KRW-BTC",
            tradeId = "1",
            price = BigDecimal.ONE,
            quantity = BigDecimal.ONE,
            side = TradeSide.BUY,
            tradedAt = Instant.EPOCH,
            receivedAt = Instant.EPOCH,
        )

    @Test
    fun `publishes inside a transaction so the publication registry can store the event`() {
        var inTransaction = false
        val transactions =
            object : TransactionOperations {
                override fun <T> execute(action: TransactionCallback<T>): T {
                    inTransaction = true
                    try {
                        return action.doInTransaction(SimpleTransactionStatus())
                    } finally {
                        inTransaction = false
                    }
                }
            }
        val publishedInTransaction = mutableListOf<Boolean>()
        val publisher = UpbitTradePublisher({ publishedInTransaction.add(inTransaction) }, transactions, meterRegistry)

        publisher.publish(event)

        assertThat(publishedInTransaction).containsExactly(true)
    }

    @Test
    fun `counts a failed publish without throwing`() {
        val publisher =
            UpbitTradePublisher(
                { throw IllegalStateException("Publish fails on purpose") },
                TransactionOperations.withoutTransaction(),
                meterRegistry,
            )

        publisher.publish(event)

        assertThat(meterRegistry.counterValue("fluxion.ingestion.upbit.messages.skipped", "reason", "publish_failed"))
            .isEqualTo(1.0)
    }
}
