package io.fluxion.ingestion.upbit

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import java.net.URI
import java.time.Duration

@ConfigurationProperties("fluxion.ingestion.upbit")
class UpbitProperties(
    // Turns the Upbit WebSocket client on or off.
    @param:DefaultValue("true") val enabled: Boolean,
    // Upbit WebSocket endpoint.
    @param:DefaultValue("wss://api.upbit.com/websocket/v1") val uri: URI,
    // Markets to subscribe to, such as KRW-BTC.
    @param:DefaultValue("KRW-BTC") val codes: List<String>,
    // How often to send a ping to keep the connection alive.
    @param:DefaultValue("30s") val pingInterval: Duration,
    // Fetches trades missed while the WebSocket was disconnected.
    @param:DefaultValue val backfill: Backfill,
) {
    class Backfill(
        // Upbit REST API base URL.
        @param:DefaultValue("https://api.upbit.com") val uri: URI,
        // Minimum time between REST requests. Upbit allows 10 trade requests per second per IP.
        @param:DefaultValue("200ms") val requestInterval: Duration,
        // Longest gap to fill. Older trades in a longer gap are skipped.
        @param:DefaultValue("1h") val maxGap: Duration,
    )
}
