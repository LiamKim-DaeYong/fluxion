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
)
