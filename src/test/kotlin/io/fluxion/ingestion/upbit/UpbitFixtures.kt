package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.transaction.support.TransactionOperations
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.time.Duration
import java.time.Instant

// Test data and wiring shared by the Upbit tests.
object UpbitFixtures {
    // Spring Boot registers the Kotlin module automatically; a plain unit test has to add it.
    val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    const val TICKS_URL = "https://api.upbit.com/v1/trades/ticks"

    // Captured from GET /v1/trades/ticks?market=KRW-BTC on 2026-10-25.
    const val CAPTURED_TICKS =
        """[{"market":"KRW-BTC","trade_date_utc":"2026-10-25","trade_time_utc":"09:35:26",""" +
            """"timestamp":1792920926054,"trade_price":107680000.00000000,"trade_volume":0.00031335,""" +
            """"prev_closing_price":108517000.00000000,"change_price":-837000.00000000,"ask_bid":"BID",""" +
            """"sequential_id":17929209260540000}]"""

    // Subscribes to KRW-BTC and backfills without waiting between requests.
    fun properties(
        uri: URI = URI.create("ws://localhost"),
        pingInterval: Duration = Duration.ofSeconds(30),
        backfillUri: URI = URI.create("https://api.upbit.com"),
    ): UpbitProperties =
        UpbitProperties(
            enabled = true,
            uri = uri,
            codes = listOf("KRW-BTC"),
            pingInterval = pingInterval,
            backfill =
                UpbitProperties.Backfill(
                    uri = backfillUri,
                    requestInterval = Duration.ZERO,
                    maxGap = Duration.ofHours(1),
                ),
        )

    // Records published events in the given collection instead of handing them to Spring.
    fun tradePublisher(
        published: MutableCollection<TradeEvent>,
        meterRegistry: MeterRegistry,
    ): UpbitTradePublisher =
        UpbitTradePublisher(
            { event -> published.add(event as TradeEvent) },
            TransactionOperations.withoutTransaction(),
            meterRegistry,
        )

    fun MeterRegistry.counterValue(
        name: String,
        vararg tags: String,
    ): Double = get(name).tags(*tags).counter().count()

    // A KRW-BTC trade message from the WebSocket.
    fun tradeMessage(
        id: Long = 1,
        tradedAt: Instant = Instant.ofEpochMilli(1),
        askBid: String = "BID",
    ): String =
        """{"type":"trade","code":"KRW-BTC","trade_price":1.0,"trade_volume":1.0,""" +
            """"ask_bid":"$askBid","trade_timestamp":${tradedAt.toEpochMilli()},"sequential_id":$id}"""

    // A KRW-BTC trade from the recent trades REST API.
    fun tick(
        id: Long,
        tradedAt: Instant,
    ): String =
        """{"market":"KRW-BTC","timestamp":${tradedAt.toEpochMilli()},"trade_price":1.0,""" +
            """"trade_volume":1.0,"ask_bid":"BID","sequential_id":$id}"""
}
