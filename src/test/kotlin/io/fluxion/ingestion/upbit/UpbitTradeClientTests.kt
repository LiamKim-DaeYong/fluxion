package io.fluxion.ingestion.upbit

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests
import org.springframework.web.client.HttpClientErrorException

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
            .expect(requestTo("$TICKS?market=KRW-BTC&count=500"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(PAGE, MediaType.APPLICATION_JSON))

        val ticks = client.fetchTicks("KRW-BTC", cursor = null, daysAgo = 0)

        assertThat(ticks.map { it.sequentialId }).containsExactly(17929209260540000)
    }

    @Test
    fun `passes cursor and days_ago when paging into an earlier day`() {
        server
            .expect(requestTo("$TICKS?market=KRW-BTC&count=500&cursor=17928863860090001&days_ago=1"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON))

        val ticks = client.fetchTicks("KRW-BTC", cursor = 17928863860090001, daysAgo = 1)

        assertThat(ticks).isEmpty()
    }

    @Test
    fun `throws when upbit rejects the request`() {
        server
            .expect(requestTo("$TICKS?market=KRW-BTC&count=500"))
            .andRespond(withTooManyRequests())

        assertThatThrownBy { client.fetchTicks("KRW-BTC", cursor = null, daysAgo = 0) }
            .isInstanceOf(HttpClientErrorException.TooManyRequests::class.java)
    }

    companion object {
        private const val TICKS = "https://api.upbit.com/v1/trades/ticks"

        // Captured from GET /v1/trades/ticks?market=KRW-BTC on 2026-10-25.
        private const val PAGE =
            """[{"market":"KRW-BTC","trade_date_utc":"2026-10-25","trade_time_utc":"09:35:26",""" +
                """"timestamp":1792920926054,"trade_price":107680000.00000000,"trade_volume":0.00031335,""" +
                """"prev_closing_price":108517000.00000000,"change_price":-837000.00000000,"ask_bid":"BID",""" +
                """"sequential_id":17929209260540000}]"""
    }
}
