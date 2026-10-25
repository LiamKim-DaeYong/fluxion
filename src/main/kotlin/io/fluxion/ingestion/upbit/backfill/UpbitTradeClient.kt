package io.fluxion.ingestion.upbit.backfill

import io.fluxion.ingestion.upbit.UpbitProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import java.util.Optional

// Calls the Upbit recent trades REST API. One call returns one page, newest first.
@Component
@ConditionalOnBooleanProperty("fluxion.ingestion.upbit.enabled", matchIfMissing = true)
class UpbitTradeClient(
    properties: UpbitProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.baseUrl(properties.backfill.uri.toString()).build()

    fun fetchTicks(
        market: String,
        cursor: Long?,
        daysAgo: Int,
    ): List<UpbitTradeTick> =
        restClient
            .get()
            .uri { builder ->
                builder
                    .path("/v1/trades/ticks")
                    .queryParam("market", market)
                    .queryParam("count", PAGE_SIZE)
                    .queryParamIfPresent("cursor", Optional.ofNullable(cursor))
                    .queryParamIfPresent("days_ago", Optional.ofNullable(daysAgo.takeIf { it > 0 }))
                    .build()
            }.retrieve()
            .body<List<UpbitTradeTick>>() ?: emptyList()

    companion object {
        // Upbit allows up to 500 trades per request.
        const val PAGE_SIZE = 500
    }
}
