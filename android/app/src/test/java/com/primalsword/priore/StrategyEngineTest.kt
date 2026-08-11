package com.primalsword.priore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StrategyEngineTest {
    private fun candle(tf: String, minute: Long, o: Double, h: Double, l: Double, c: Double) = Candle(
        timeframe = tf,
        openedAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(minute * 60),
        open = o,
        high = h,
        low = l,
        close = c,
        volume = 10,
    )

    @Test
    fun waitsWithoutHistory() {
        val plan = StrategyEngine().evaluate()
        assertEquals(SignalKind.WAIT, plan.kind)
    }

    @Test
    fun detectsBearishRejectionWhenM15IsBearish() {
        val engine = StrategyEngine(levelLookback = 12, minRr = 2.0)

        val m15 = (0 until 70).map { index ->
            val close = 4500.0 - index * 1.5
            candle("M15", index * 15L, close + 0.5, close + 1.0, close - 1.0, close)
        }
        engine.seed("M15", m15)

        val m5 = (0 until 30).map { index ->
            val close = 4380.0 - index * 0.20
            candle("M5", index * 5L, close + 0.10, close + 0.50, close - 0.50, close)
        }
        engine.seed("M5", m5)

        val resistance = m5.takeLast(12).maxOf { it.high }
        val last = candle(
            "M5",
            30 * 5L,
            resistance - 1.0,
            resistance + 0.50,
            resistance - 1.8,
            resistance - 1.2,
        )
        val plan = engine.onClosedCandle(last)

        assertNotNull(plan)
        assertEquals(SignalKind.SELL_SETUP, plan!!.kind)
        assertTrue(plan.stop!! > plan.entry!!)
        assertTrue(plan.target!! < plan.entry!!)
        assertEquals(2.0, plan.riskReward!!, 0.0001)
    }

    @Test
    fun watchesBuyNearSupportBeforeConfirmation() {
        val engine = StrategyEngine(levelLookback = 12)

        val m15 = (0 until 70).map { index ->
            val close = 4300.0 + index * 1.5
            candle("M15", index * 15L, close - 0.5, close + 1.0, close - 1.0, close)
        }
        engine.seed("M15", m15)

        val m5 = (0 until 30).map { index ->
            val close = 4380.0 + (index % 3) * 0.20
            candle("M5", index * 5L, close, close + 0.50, close - 0.50, close)
        }
        engine.seed("M5", m5)

        val last = candle(
            "M5",
            30 * 5L,
            4379.90,
            4380.10,
            4379.80,
            4379.85,
        )
        val plan = engine.onClosedCandle(last)

        assertNotNull(plan)
        assertEquals(SignalKind.WATCH_BUY, plan!!.kind)
        assertTrue(plan.nextTrigger.isNotBlank())
        assertTrue(plan.invalidation.isNotBlank())
    }
}
