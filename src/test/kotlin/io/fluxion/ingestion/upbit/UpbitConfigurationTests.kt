package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.upbit.backfill.UpbitBackfill
import io.fluxion.ingestion.upbit.backfill.UpbitTradeClient
import io.fluxion.ingestion.upbit.websocket.UpbitMessageHandler
import io.fluxion.ingestion.upbit.websocket.UpbitWebSocketClient
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionOperations
import org.springframework.web.client.RestClient
import java.net.URI
import java.time.Duration

// Checks conditional registration and property binding without starting the whole application.
class UpbitConfigurationTests {
    private val contextRunner =
        ApplicationContextRunner()
            .withUserConfiguration(
                InfrastructureConfiguration::class.java,
                UpbitTradePublisher::class.java,
                UpbitMessageHandler::class.java,
                UpbitTradeClient::class.java,
                UpbitBackfill::class.java,
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
    fun `does not register the other upbit beans when disabled`() {
        contextRunner
            .withPropertyValues("fluxion.ingestion.upbit.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(UpbitTradePublisher::class.java)
                assertThat(context).doesNotHaveBean(UpbitMessageHandler::class.java)
                assertThat(context).doesNotHaveBean(UpbitTradeClient::class.java)
                assertThat(context).doesNotHaveBean(UpbitBackfill::class.java)
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
                assertThat(properties.backfill.uri).isEqualTo(URI.create("https://api.upbit.com"))
                assertThat(properties.backfill.requestInterval).isEqualTo(Duration.ofMillis(200))
                assertThat(properties.backfill.maxGap).isEqualTo(Duration.ofHours(1))
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
                "fluxion.ingestion.upbit.backfill.uri=http://localhost:8080",
                "fluxion.ingestion.upbit.backfill.request-interval=1s",
                "fluxion.ingestion.upbit.backfill.max-gap=10m",
            ).run { context ->
                val properties = context.getBean(UpbitProperties::class.java)

                assertThat(properties.enabled).isFalse()
                assertThat(properties.uri).isEqualTo(URI.create("ws://localhost:8080/websocket/v1"))
                assertThat(properties.codes).containsExactly("KRW-BTC", "KRW-ETH")
                assertThat(properties.pingInterval).isEqualTo(Duration.ofSeconds(10))
                assertThat(properties.backfill.uri).isEqualTo(URI.create("http://localhost:8080"))
                assertThat(properties.backfill.requestInterval).isEqualTo(Duration.ofSeconds(1))
                assertThat(properties.backfill.maxGap).isEqualTo(Duration.ofMinutes(10))
            }
    }

    // Beans that Spring Boot auto-configuration would provide in the real application.
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UpbitProperties::class)
    class InfrastructureConfiguration {
        @Bean
        fun jsonMapper() = UpbitFixtures.jsonMapper

        @Bean
        fun meterRegistry() = SimpleMeterRegistry()

        @Bean
        fun restClientBuilder(): RestClient.Builder = RestClient.builder()

        // Lets the trade publisher run without a real transaction manager.
        @Bean
        fun transactionOperations(): TransactionOperations = TransactionOperations.withoutTransaction()
    }
}
