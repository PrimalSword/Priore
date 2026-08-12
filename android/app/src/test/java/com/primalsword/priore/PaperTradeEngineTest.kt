package com.primalsword.priore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PaperTradeEngineTest {
    private val now = Instant.parse("2026-08-12T12:00:00Z")

    @Test
    fun opensSellSimulationFromConfirmedPlan() {
        val plan = TradePlan(
            kind = SignalKind.SELL_SETUP,
            reason = "teste",
            entry = 4376.80,
            stop = 4386.71,
            target = 4358.96,
            riskReward = 1.8,
            trendM15 = "bearish",
        )

        val trade = PaperTradeEngine.open(plan, 4376.50, now)

        assertNotNull(trade)
        assertEquals(PaperTradeStatus.OPEN, trade!!.status)
        assertEquals(4376.80, trade.entry, 0.0001)
        assertEquals(4376.50, trade.currentPrice!!, 0.0001)
    }

    @Test
    fun sellWinsWhenTargetIsTouched() {
        val trade = PaperTrade(
            id = "x",
            signalKind = SignalKind.SELL_SETUP,
            status = PaperTradeStatus.OPEN,
            entry = 4376.80,
            stop = 4386.71,
            target = 4358.96,
            riskReward = 1.8,
            createdAt = now,
            updatedAt = now,
        )

        val closed = PaperTradeEngine.onPrice(trade, 4358.90, now.plusSeconds(600))

        assertEquals(PaperTradeStatus.WIN, closed.status)
        assertTrue(closed.resultPoints()!! > 0.0)
    }

    @Test
    fun buyLosesWhenStopIsTouched() {
        val trade = PaperTrade(
            id = "x",
            signalKind = SignalKind.BUY_SETUP,
            status = PaperTradeStatus.OPEN,
            entry = 4400.0,
            stop = 4390.0,
            target = 4418.0,
            riskReward = 1.8,
            createdAt = now,
            updatedAt = now,
        )

        val closed = PaperTradeEngine.onPrice(trade, 4389.90, now.plusSeconds(300))

        assertEquals(PaperTradeStatus.LOSS, closed.status)
        assertTrue(closed.resultPoints()!! < 0.0)
    }
}
