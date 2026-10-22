package io.fluxion.ingestion.upbit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

// Runs the client without Spring against a local server that stands in for Upbit.
class UpbitWebSocketClientTests {
    private lateinit var server: UpbitTestServer
    private lateinit var client: UpbitWebSocketClient

    @AfterEach
    fun tearDown() {
        client.stop()
        server.close()
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
    fun `reconnects and subscribes again when the server closes the connection`() {
        startClient()
        server.awaitConnection()
        server.awaitMessage()

        server.closeConnection()

        server.awaitConnection()
        assertThat(server.awaitMessage()).contains("\"type\":\"trade\"")
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
        client =
            UpbitWebSocketClient(
                UpbitProperties(
                    enabled = true,
                    uri = server.uri,
                    codes = listOf("KRW-BTC"),
                    pingInterval = pingInterval,
                ),
                JsonMapper(),
            )
        client.start()
    }
}
