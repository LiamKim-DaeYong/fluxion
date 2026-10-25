package io.fluxion.ingestion.upbit

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionOperations
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration

// Checks conditional registration and property binding without starting the whole application.
class UpbitConfigurationTests {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(
                PropertiesConfiguration::class.java,
                UpbitTradeHandler::class.java,
                UpbitWebSocketClient::class.java,
            )

    @Test
    fun `registers the client when enabled is not set`() {
        UpbitTestServer().use { server ->
            contextRunner
                .withPropertyValues("fluxion.ingestion.upbit.uri=${server.uri}")
                .run { context ->
                    assertThat(context).hasSingleBean(UpbitWebSocketClient::class.java)
                    // The context starts the client, so wait for it to connect before the context closes.
                    server.awaitConnection()
                }
        }
    }

    @Test
    fun `does not register the client when disabled`() {
        contextRunner
            .withPropertyValues("fluxion.ingestion.upbit.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(UpbitWebSocketClient::class.java)
                assertThat(context).hasSingleBean(UpbitProperties::class.java)
            }
    }

    @Test
    fun `binds default properties`() {
        contextRunner
            .withPropertyValues("fluxion.ingestion.upbit.enabled=false")
            .run { context ->
                val properties = context.getBean(UpbitProperties::class.java)

                assertThat(properties.uri).isEqualTo(URI.create("wss://api.upbit.com/websocket/v1"))
                assertThat(properties.codes).containsExactly("KRW-BTC")
                assertThat(properties.pingInterval).isEqualTo(Duration.ofSeconds(30))
            }
    }

    @Test
    fun `binds custom properties`() {
        contextRunner
            .withPropertyValues(
                "fluxion.ingestion.upbit.enabled=false",
                "fluxion.ingestion.upbit.uri=ws://localhost:8080/websocket/v1",
                "fluxion.ingestion.upbit.codes=KRW-BTC,KRW-ETH",
                "fluxion.ingestion.upbit.ping-interval=10s",
            ).run { context ->
                val properties = context.getBean(UpbitProperties::class.java)

                assertThat(properties.enabled).isFalse()
                assertThat(properties.uri).isEqualTo(URI.create("ws://localhost:8080/websocket/v1"))
                assertThat(properties.codes).containsExactly("KRW-BTC", "KRW-ETH")
                assertThat(properties.pingInterval).isEqualTo(Duration.ofSeconds(10))
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UpbitProperties::class)
    class PropertiesConfiguration {
        @Bean
        fun jsonMapper() = JsonMapper()

        @Bean
        fun meterRegistry() = SimpleMeterRegistry()

        // Lets the trade handler run without a real transaction manager.
        @Bean
        fun transactionOperations(): TransactionOperations = TransactionOperations.withoutTransaction()
    }
}
