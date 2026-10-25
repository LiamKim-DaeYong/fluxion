package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionOperations
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.fail

// Runs the client without Spring against a local server that stands in for Upbit.
class UpbitWebSocketClientTests {
    private lateinit var server: UpbitTestServer
    private lateinit var client: UpbitWebSocketClient

    private val meterRegistry = SimpleMeterRegistry()

    // Events the client published, recorded by a fake publisher instead of Spring.
    private val publishedEvents = LinkedBlockingQueue<Any>()

    @AfterEach
    fun tearDown() {
        client.stop()
        if (::server.isInitialized) {
            server.close()
        }
    }

    @Test
    fun `subscribes to trades after connecting`() {
        startClient()

        server.awaitConnection()

        assertThat(server.awaitMessage())
            .contains("\"type\":\"trade\"")
            .contains("\"codes\":[\"KRW-BTC\"]")
    }

    @Test
    fun `counts each text and binary message`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.sendText("""{"type":"trade"}""")
        server.sendBinary("""{"type":"trade"}""")

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(meterRegistry.get("fluxion.ingestion.upbit.messages.received").counter().count())
                .isEqualTo(2.0)
        }
    }

    @Test
    fun `publishes a trade event for a trade message`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.sendText(TRADE_MESSAGE)

        val event = publishedEvents.poll(5, TimeUnit.SECONDS) as TradeEvent
        assertThat(event.symbol).isEqualTo("KRW-BTC")
        assertThat(event.tradeId).isEqualTo("1")
    }

    @Test
    fun `skips an invalid message and keeps receiving`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.sendText("""{"type":"trade"}""")
        server.sendText(TRADE_MESSAGE)

        // Messages are handled in order, so the invalid one has been counted once the valid one is published.
        assertThat(publishedEvents.poll(5, TimeUnit.SECONDS)).isInstanceOf(TradeEvent::class.java)
        assertThat(
            meterRegistry
                .get("fluxion.ingestion.upbit.messages.skipped")
                .tag("reason", "invalid")
                .counter()
                .count(),
        ).isEqualTo(1.0)
    }

    @Test
    fun `reconnects and subscribes again when the server closes the connection`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.closeConnection()

        server.awaitConnection()
        assertThat(server.awaitMessage()).contains("\"type\":\"trade\"")
    }

    @Test
    fun `reconnects when the connection drops without a close frame`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.dropConnection()

        server.awaitConnection()
        assertThat(server.awaitMessage()).contains("\"type\":\"trade\"")
    }

    @Test
    fun `does not backfill on the first connection`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.assertNoTicksRequest(Duration.ofSeconds(1))
    }

    @Test
    fun `backfills trades since the last received trade after reconnecting`() {
        val lastTradedAt = Instant.now().minusSeconds(60)
        startClient()
        server.awaitConnection()
        server.awaitMessage()
        server.sendText(tradeMessage(id = 1, tradedAt = lastTradedAt))
        assertThat((publishedEvents.poll(5, TimeUnit.SECONDS) as TradeEvent).tradeId).isEqualTo("1")

        // Missed while disconnected: 3 is newer than the last received trade, 1 is the trade itself.
        server.ticksResponse = "[${tick(id = 3, tradedAt = lastTradedAt.plusSeconds(5))},${tick(id = 1, tradedAt = lastTradedAt)}]"
        server.closeConnection()
        server.awaitConnection()

        assertThat(server.awaitTicksRequest()).contains("market=KRW-BTC")
        // The trade itself is published again and dropped later by the recorder's ON CONFLICT.
        val backfilled = List(2) { (publishedEvents.poll(5, TimeUnit.SECONDS) as TradeEvent).tradeId }
        assertThat(backfilled).containsExactly("3", "1")
    }

    @Test
    fun `retries a failed handshake with a growing delay`() {
        // Answers every handshake with 404, as Upbit does for an unknown path.
        // Closing without a response makes the JDK client retry the request at once, so each attempt would count twice.
        ServerSocket(0).use { rejecting ->
            val attempts = LinkedBlockingQueue<Long>()
            Thread.ofVirtual().start {
                try {
                    while (true) {
                        rejecting.accept().use { socket ->
                            attempts.add(System.nanoTime())
                            val request = socket.getInputStream().bufferedReader()
                            while (!request.readLine().isNullOrEmpty()) {
                                // Read the request headers before answering.
                            }
                            socket.getOutputStream().write(NOT_FOUND)
                        }
                    }
                } catch (_: SocketException) {
                    // The socket was closed at the end of the test.
                }
            }

            startClientAt(URI.create("ws://localhost:${rejecting.localPort}/websocket/v1"))

            val times = List(3) { attempts.poll(5, TimeUnit.SECONDS) ?: fail("No connection attempt within 5s") }
            val firstDelay = Duration.ofNanos(times[1] - times[0])
            val secondDelay = Duration.ofNanos(times[2] - times[1])

            // Backoff starts at 1 second and doubles.
            assertThat(firstDelay).isGreaterThanOrEqualTo(Duration.ofMillis(900))
            assertThat(secondDelay).isGreaterThan(firstDelay)
        }
    }

    @Test
    fun `does not reconnect after the client stops`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        client.stop()

        // Longer than the first reconnect delay of 1 second.
        server.assertNoConnection(Duration.ofSeconds(2))
    }

    @Test
    fun `keeps the connection while the server answers pings`() {
        startClient(pingInterval = Duration.ofMillis(200))
        server.awaitConnection()

        // About 10 pings. A missing pong would drop the connection and open a new one.
        server.assertNoConnection(Duration.ofSeconds(2))
    }

    @Test
    fun `drops the connection and reconnects when the server stops answering pings`() {
        startClient(answerPings = false, pingInterval = Duration.ofMillis(200))
        server.awaitConnection()

        // Ping at 200ms, no pong by 400ms, then reconnect after 1 second.
        server.awaitConnection()
    }

    private fun startClient(
        answerPings: Boolean = true,
        pingInterval: Duration = Duration.ofSeconds(30),
    ) {
        server = UpbitTestServer(answerPings)
        startClientAt(server.uri, pingInterval, server.restUri)
    }

    private fun startClientAt(
        uri: URI,
        pingInterval: Duration = Duration.ofSeconds(30),
        restUri: URI = URI.create("http://localhost"),
    ) {
        val jsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
        val events = ApplicationEventPublisher { publishedEvents.add(it) }
        val properties =
            UpbitProperties(
                enabled = true,
                uri = uri,
                codes = listOf("KRW-BTC"),
                pingInterval = pingInterval,
                backfill =
                    UpbitProperties.Backfill(
                        uri = restUri,
                        requestInterval = Duration.ZERO,
                        maxGap = Duration.ofHours(1),
                    ),
            )
        val tradeHandler = UpbitTradeHandler(jsonMapper, events, TransactionOperations.withoutTransaction(), meterRegistry)
        val backfill =
            UpbitBackfill(properties, UpbitTradeClient(properties, RestClient.builder()), tradeHandler, meterRegistry)
        client = UpbitWebSocketClient(properties, jsonMapper, tradeHandler, backfill, meterRegistry)
        client.start()
    }

    companion object {
        private const val TRADE_MESSAGE =
            """{"type":"trade","code":"KRW-BTC","trade_price":1.0,"trade_volume":1.0,""" +
                """"ask_bid":"BID","trade_timestamp":1,"sequential_id":1}"""

        private fun tradeMessage(
            id: Long,
            tradedAt: Instant,
        ): String =
            """{"type":"trade","code":"KRW-BTC","trade_price":1.0,"trade_volume":1.0,""" +
                """"ask_bid":"BID","trade_timestamp":${tradedAt.toEpochMilli()},"sequential_id":$id}"""

        private fun tick(
            id: Long,
            tradedAt: Instant,
        ): String =
            """{"market":"KRW-BTC","timestamp":${tradedAt.toEpochMilli()},"trade_price":1.0,""" +
                """"trade_volume":1.0,"ask_bid":"BID","sequential_id":$id}"""

        private val NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
    }
}
