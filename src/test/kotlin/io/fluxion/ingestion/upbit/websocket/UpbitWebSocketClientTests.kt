package io.fluxion.ingestion.upbit.websocket

import io.fluxion.ingestion.TradeEvent
import io.fluxion.ingestion.upbit.UpbitFixtures
import io.fluxion.ingestion.upbit.UpbitFixtures.tick
import io.fluxion.ingestion.upbit.UpbitFixtures.tradeMessage
import io.fluxion.ingestion.upbit.UpbitTestServer
import io.fluxion.ingestion.upbit.backfill.UpbitBackfill
import io.fluxion.ingestion.upbit.backfill.UpbitTradeClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
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
    private lateinit var backfill: UpbitBackfill
    private lateinit var client: UpbitWebSocketClient

    private val meterRegistry = SimpleMeterRegistry()

    // Trades the client and the backfill published, recorded instead of handed to Spring.
    private val publishedEvents = LinkedBlockingQueue<TradeEvent>()

    @AfterEach
    fun tearDown() {
        client.stop()
        backfill.stop()
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
    fun `publishes trades from both text and binary frames`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.sendText(tradeMessage(id = 1))
        server.sendBinary(tradeMessage(id = 2))

        assertThat(nextTradeIds(2)).containsExactly("1", "2")
    }

    @Test
    fun `keeps receiving after an invalid message`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.sendText("""{"type":"trade"}""")
        server.sendText(tradeMessage())

        assertThat(nextTradeIds(1)).containsExactly("1")
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
        assertThat(nextTradeIds(1)).containsExactly("1")

        // Missed while disconnected: 3 is newer than the last received trade, 1 is the trade itself.
        server.ticksResponse = "[${tick(id = 3, tradedAt = lastTradedAt.plusSeconds(5))},${tick(id = 1, tradedAt = lastTradedAt)}]"
        server.closeConnection()
        server.awaitConnection()

        assertThat(server.awaitTicksRequest()).contains("market=KRW-BTC")
        // The trade itself is published again and dropped later by the recorder's ON CONFLICT.
        assertThat(nextTradeIds(2)).containsExactly("3", "1")
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

    // Wires the client the way Spring would and starts the backfill before the client, as Spring does.
    private fun startClientAt(
        uri: URI,
        pingInterval: Duration = Duration.ofSeconds(30),
        restUri: URI = URI.create("http://localhost"),
    ) {
        val properties = UpbitFixtures.properties(uri, pingInterval, restUri)
        val tradePublisher = UpbitFixtures.tradePublisher(publishedEvents, meterRegistry)
        val messageHandler = UpbitMessageHandler(UpbitFixtures.jsonMapper, tradePublisher, meterRegistry)
        backfill = UpbitBackfill(properties, UpbitTradeClient(properties, RestClient.builder()), tradePublisher, meterRegistry)
        client = UpbitWebSocketClient(properties, UpbitFixtures.jsonMapper, messageHandler, backfill)
        backfill.start()
        client.start()
    }

    // Waits for the next published trades and returns their ids in publish order.
    private fun nextTradeIds(count: Int): List<String> =
        List(count) { (publishedEvents.poll(5, TimeUnit.SECONDS) ?: fail("No trade published within 5s")).tradeId }

    companion object {
        private val NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
    }
}
