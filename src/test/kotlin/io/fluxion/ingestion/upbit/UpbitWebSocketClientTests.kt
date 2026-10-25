package io.fluxion.ingestion.upbit

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.fail

// Runs the client without Spring against a local server that stands in for Upbit.
class UpbitWebSocketClientTests {
    private lateinit var server: UpbitTestServer
    private lateinit var client: UpbitWebSocketClient

    private val meterRegistry = SimpleMeterRegistry()

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
        startClientAt(server.uri, pingInterval)
    }

    private fun startClientAt(
        uri: URI,
        pingInterval: Duration = Duration.ofSeconds(30),
    ) {
        client =
            UpbitWebSocketClient(
                UpbitProperties(
                    enabled = true,
                    uri = uri,
                    codes = listOf("KRW-BTC"),
                    pingInterval = pingInterval,
                ),
                JsonMapper(),
                meterRegistry,
            )
        client.start()
    }

    companion object {
        private val NOT_FOUND = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()
    }
}
