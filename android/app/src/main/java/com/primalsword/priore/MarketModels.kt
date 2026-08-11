package com.primalsword.priore

import java.time.Instant

data class Candle(
    val timeframe: String,
    val openedAt: Instant,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
) {
    val body: Double get() = kotlin.math.abs(close - open)
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
}

enum class SignalKind { WAIT, BUY_SETUP, SELL_SETUP }

data class TradePlan(
    val kind: SignalKind,
    val reason: String,
    val entry: Double? = null,
    val stop: Double? = null,
    val target: Double? = null,
    val riskReward: Double? = null,
    val trendM15: String = "neutral",
    val support: Double? = null,
    val resistance: Double? = null,
    val atrM5: Double? = null,
)
