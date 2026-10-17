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
import org.springframework.modulith.events.CompletedEventPublications
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.support.TransactionTemplate

@Import(TestcontainersConfiguration::class, EventPublicationRegistryIntegrationTests.ListenerConfiguration::class)
@SpringBootTest
class EventPublicationRegistryIntegrationTests {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    lateinit var events: ApplicationEventPublisher

    @Autowired
    lateinit var completedPublications: CompletedEventPublications

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

    @Test
    fun `registry stores a publication and marks it completed after the listener succeeds`() {
        val event = SampleEvent("registry-check")

        transactionTemplate.executeWithoutResult { events.publishEvent(event) }

        assertThat(completedPublications.findAll())
            .anySatisfy { assertThat(it.event).isEqualTo(event) }
    }

    data class SampleEvent(
        val value: String,
    )

    // Modulith wraps transactional listeners in a CGLIB proxy to record completion, so the class must be open.
    open class SampleListener {
        @TransactionalEventListener
        open fun on(event: SampleEvent) = Unit
    }

    @TestConfiguration(proxyBeanMethods = false)
    class ListenerConfiguration {
        @Bean
        fun sampleListener() = SampleListener()
    }
}
