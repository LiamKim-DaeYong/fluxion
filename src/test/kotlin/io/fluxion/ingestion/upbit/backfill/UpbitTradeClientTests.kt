package io.fluxion.ingestion.upbit.backfill

import io.fluxion.ingestion.upbit.UpbitFixtures.CAPTURED_TICKS
import io.fluxion.ingestion.upbit.UpbitFixtures.TICKS_URL
import io.fluxion.ingestion.upbit.UpbitProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

// Checks the request the client sends and how it reads the response, against a mock Upbit server.
@RestClientTest(UpbitTradeClient::class)
@EnableConfigurationProperties(UpbitProperties::class)
class UpbitTradeClientTests {
    @Autowired
    lateinit var client: UpbitTradeClient

    @Autowired
    lateinit var server: MockRestServiceServer

    @AfterEach
    fun verifyRequests() {
        server.verify()
    }

    @Test
    fun `requests the latest page of today without cursor and days_ago`() {
        server
            .expect(requestTo("$TICKS_URL?market=KRW-BTC&count=500"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(CAPTURED_TICKS, MediaType.APPLICATION_JSON))

        val ticks = client.fetchTicks("KRW-BTC", cursor = null, daysAgo = 0)

        assertThat(ticks.map { it.sequentialId }).containsExactly(17929209260540000)
    }

    @Test
    fun `passes cursor and days_ago when paging into an earlier day`() {
        server
            .expect(requestTo("$TICKS_URL?market=KRW-BTC&count=500&cursor=17928863860090001&days_ago=1"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val ticks = client.fetchTicks("KRW-BTC", cursor = 17928863860090001, daysAgo = 1)

        assertThat(ticks).isEmpty()
    }
}
