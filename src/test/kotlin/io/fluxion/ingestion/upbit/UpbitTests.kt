package io.fluxion.ingestion.upbit

import io.fluxion.ingestion.TradeSide
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UpbitTests {
    @Test
    fun `maps bid to buy and ask to sell`() {
        assertThat(Upbit.side("BID")).isEqualTo(TradeSide.BUY)
        assertThat(Upbit.side("ASK")).isEqualTo(TradeSide.SELL)
    }

    @Test
    fun `rejects an unknown ask_bid value`() {
        assertThatThrownBy { Upbit.side("UNKNOWN") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
