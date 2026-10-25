package io.fluxion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

@Import(TestcontainersConfiguration::class, EventPublicationRegistryIntegrationTests.ListenerConfiguration::class)
@SpringBootTest(properties = ["fluxion.ingestion.upbit.enabled=false"])
class EventPublicationRegistryIntegrationTests {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @Test
    fun `flyway creates the event publication table with the Modulith 2 schema`() {
        val columns =
            jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_name = 'event_publication'",
                String::class.java,
            )

        assertThat(columns).containsExactlyInAnyOrder(
            "id",
            "listener_id",
            "event_type",
            "serialized_event",
            "publication_date",
            "completion_date",
            "status",
            "completion_attempts",
            "last_resubmission_date",
        )
    }

    // completion-mode is delete, so a completed publication leaves no row behind.
    @Test
    fun `registry deletes a publication once the listener succeeds`() {
        publish(SampleEvent("succeeds"))

        assertThat(publications("succeeds")).isEmpty()
    }

    // Also proves the registry recorded the publication, which the test above cannot show on its own.
    @Test
    fun `registry keeps a publication incomplete when the listener fails`() {
        publish(SampleEvent(FAILING_VALUE))

        assertThat(publications(FAILING_VALUE))
            .singleElement()
            .satisfies({ assertThat(it["completion_date"]).isNull() })
    }

    private fun publish(event: SampleEvent) {
        transactionTemplate.executeWithoutResult { events.publishEvent(event) }
    }

    private fun publications(value: String): List<Map<String, Any?>> =
        jdbcTemplate.queryForList(
            "select completion_date from event_publication where serialized_event like ?",
            "%\"$value\"%",
        )

    data class SampleEvent(
        val value: String,
    )

    // Modulith wraps transactional listeners in a CGLIB proxy to record completion, so the class must be open.
    open class SampleListener {
        @TransactionalEventListener
        open fun on(event: SampleEvent) {
            check(event.value != FAILING_VALUE) { "Listener fails on purpose" }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ListenerConfiguration {
        @Bean
        fun sampleListener() = SampleListener()
    }

    companion object {
        private const val FAILING_VALUE = "fails"
    }
}
