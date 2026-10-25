package io.fluxion.marketdata.trade

import io.fluxion.ingestion.TradeEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

// Stores trades published by the ingestion module.
@Component
class TradeRecorder(
    private val trades: TradeRepository,
    meterRegistry: MeterRegistry,
) {
    // Counts stored trades and duplicates that were already in the table.
    private val inserted = recordedCounter(meterRegistry, "inserted")
    private val duplicates = recordedCounter(meterRegistry, "duplicate")

    // Runs after the publishing transaction commits, on another thread and in a new transaction.
    // If it throws, the publication stays incomplete in event_publication and can be resubmitted.
    @ApplicationModuleListener
    fun on(trade: TradeEvent) {
        if (trades.insertIfAbsent(trade)) {
            inserted.increment()
        } else {
            duplicates.increment()
        }
    }

    companion object {
        private fun recordedCounter(
            meterRegistry: MeterRegistry,
            result: String,
        ): Counter =
            Counter
                .builder("fluxion.marketdata.trades.recorded")
                .description("Trades handled by the recorder, by result")
                .tag("result", result)
                .register(meterRegistry)
    }
}
