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

enum class SignalKind {
    WAIT,
    WATCH_BUY,
    WATCH_SELL,
    BUY_SETUP,
    SELL_SETUP,
}

data class TradePlan(
    val kind: SignalKind,
    val reason: String,
    val nextTrigger: String = "",
    val invalidation: String = "",
    val entry: Double? = null,
    val stop: Double? = null,
    val target: Double? = null,
    val riskReward: Double? = null,
    val trendM15: String = "neutral",
    val support: Double? = null,
    val resistance: Double? = null,
    val atrM5: Double? = null,
)

enum class SetupStatus {
    PENDING_EXECUTION,
    OPEN,
    WIN,
    LOSS,
    INVALIDATED,
    EXPIRED,
    ERROR,
}

data class ActiveSetup(
    val setupId: String,
    val signalKind: SignalKind,
    val status: SetupStatus,
    val signalEntry: Double,
    val stop: Double,
    val target: Double,
    val riskReward: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    val actualEntry: Double? = null,
    val currentPrice: Double? = null,
    val positionId: Long? = null,
    val orderId: Long? = null,
    val volume: Long? = null,
    val closePrice: Double? = null,
    val grossProfit: Double? = null,
    val note: String = "",
) {
    val isTerminal: Boolean
        get() = status in setOf(
            SetupStatus.WIN,
            SetupStatus.LOSS,
            SetupStatus.INVALIDATED,
            SetupStatus.EXPIRED,
            SetupStatus.ERROR,
        )
}

data class DemoExecutionEvent(
    val executionType: Int,
    val positionId: Long? = null,
    val orderId: Long? = null,
    val executionPrice: Double? = null,
    val positionStatus: Int? = null,
    val grossProfit: Double? = null,
    val label: String = "",
    val errorCode: String = "",
    val description: String = "",
)
