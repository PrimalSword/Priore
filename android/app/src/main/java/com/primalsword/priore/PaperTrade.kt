package com.primalsword.priore

import java.time.Instant
import java.util.UUID

enum class PaperTradeStatus {
    OPEN,
    WIN,
    LOSS,
}

data class PaperTrade(
    val id: String,
    val signalKind: SignalKind,
    val status: PaperTradeStatus,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val riskReward: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    val closedAt: Instant? = null,
    val currentPrice: Double? = null,
    val closePrice: Double? = null,
    val trendM15: String = "neutral",
    val support: Double? = null,
    val resistance: Double? = null,
    val atrM5: Double? = null,
    val reason: String = "",
) {
    val isOpen: Boolean get() = status == PaperTradeStatus.OPEN

    fun pointsAt(price: Double): Double = when (signalKind) {
        SignalKind.BUY_SETUP -> price - entry
        SignalKind.SELL_SETUP -> entry - price
        else -> 0.0
    }

    fun resultPoints(): Double? = closePrice?.let(::pointsAt)
}

object PaperTradeEngine {
    fun open(plan: TradePlan, currentPrice: Double?, now: Instant = Instant.now()): PaperTrade? {
        if (plan.kind != SignalKind.BUY_SETUP && plan.kind != SignalKind.SELL_SETUP) return null
        val entry = plan.entry ?: return null
        val stop = plan.stop ?: return null
        val target = plan.target ?: return null
        return PaperTrade(
            id = "paper-${UUID.randomUUID()}",
            signalKind = plan.kind,
            status = PaperTradeStatus.OPEN,
            entry = entry,
            stop = stop,
            target = target,
            riskReward = plan.riskReward ?: 1.8,
            createdAt = now,
            updatedAt = now,
            currentPrice = currentPrice ?: entry,
            trendM15 = plan.trendM15,
            support = plan.support,
            resistance = plan.resistance,
            atrM5 = plan.atrM5,
            reason = plan.reason,
        )
    }

    fun onPrice(trade: PaperTrade, price: Double, now: Instant = Instant.now()): PaperTrade {
        if (!trade.isOpen) return trade

        val outcome = when (trade.signalKind) {
            SignalKind.BUY_SETUP -> when {
                price <= trade.stop -> PaperTradeStatus.LOSS
                price >= trade.target -> PaperTradeStatus.WIN
                else -> PaperTradeStatus.OPEN
            }
            SignalKind.SELL_SETUP -> when {
                price >= trade.stop -> PaperTradeStatus.LOSS
                price <= trade.target -> PaperTradeStatus.WIN
                else -> PaperTradeStatus.OPEN
            }
            else -> PaperTradeStatus.OPEN
        }

        return if (outcome == PaperTradeStatus.OPEN) {
            trade.copy(currentPrice = price, updatedAt = now)
        } else {
            trade.copy(
                status = outcome,
                currentPrice = price,
                closePrice = price,
                updatedAt = now,
                closedAt = now,
            )
        }
    }
}
