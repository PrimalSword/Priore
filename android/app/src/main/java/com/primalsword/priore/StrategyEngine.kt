package com.primalsword.priore

import java.util.ArrayDeque

class StrategyEngine(
    private val atrPeriod: Int = 14,
    private val levelLookback: Int = 12,
    private val minRr: Double = 1.8,
) {
    private val m5 = ArrayDeque<Candle>()
    private val m15 = ArrayDeque<Candle>()

    @Synchronized
    fun seed(timeframe: String, candles: List<Candle>) {
        val target = series(timeframe)
        target.clear()
        candles.sortedBy { it.openedAt }.forEach { upsert(target, it) }
    }

    @Synchronized
    fun onClosedCandle(candle: Candle): TradePlan? {
        val target = series(candle.timeframe)
        if (!upsert(target, candle) || candle.timeframe != "M5") return null
        return evaluate()
    }

    @Synchronized
    fun evaluate(): TradePlan {
        val bars5 = m5.toList()
        val bars15 = m15.toList()
        if (bars5.size < maxOf(atrPeriod + 2, levelLookback + 2) || bars15.size < 50) {
            return TradePlan(SignalKind.WAIT, "Aguardando histórico suficiente.")
        }

        val current = bars5.last()
        val window = bars5.dropLast(1).takeLast(levelLookback)
        val support = window.minOf { it.low }
        val resistance = window.maxOf { it.high }
        val atr = atr(bars5, atrPeriod)
            ?: return TradePlan(SignalKind.WAIT, "ATR indisponível.")
        val trend = m15Trend(bars15)
        val tolerance = 0.18 * atr
        val body = maxOf(current.body, atr * 0.03)

        val bearishRejection = current.high >= resistance - tolerance &&
            current.close < resistance - 0.12 * atr &&
            current.upperWick >= body * 1.15
        val bullishRejection = current.low <= support + tolerance &&
            current.close > support + 0.12 * atr &&
            current.lowerWick >= body * 1.15
        val breakdown = current.close < support - 0.08 * atr
        val breakout = current.close > resistance + 0.08 * atr

        if (trend == "bearish" && (bearishRejection || breakdown)) {
            val entry = current.close
            val stop = maxOf(current.high, resistance) + 0.25 * atr
            val risk = stop - entry
            val target = entry - minRr * risk
            return TradePlan(
                kind = SignalKind.SELL_SETUP,
                reason = "M15 baixista com rejeição/rompimento vendedor confirmado no fechamento M5.",
                entry = entry,
                stop = stop,
                target = target,
                riskReward = minRr,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        if (trend == "bullish" && (bullishRejection || breakout)) {
            val entry = current.close
            val stop = minOf(current.low, support) - 0.25 * atr
            val risk = entry - stop
            val target = entry + minRr * risk
            return TradePlan(
                kind = SignalKind.BUY_SETUP,
                reason = "M15 altista com rejeição/rompimento comprador confirmado no fechamento M5.",
                entry = entry,
                stop = stop,
                target = target,
                riskReward = minRr,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        return TradePlan(
            kind = SignalKind.WAIT,
            reason = "Sem confirmação suficiente no fechamento M5.",
            trendM15 = trend,
            support = support,
            resistance = resistance,
            atrM5 = atr,
        )
    }

    private fun series(timeframe: String): ArrayDeque<Candle> = when (timeframe.uppercase()) {
        "M5" -> m5
        "M15" -> m15
        else -> error("Timeframe não suportado: $timeframe")
    }

    private fun upsert(series: ArrayDeque<Candle>, candle: Candle): Boolean {
        val last = series.lastOrNull()
        if (last != null && last.openedAt == candle.openedAt) {
            if (last == candle) return false
            series.removeLast()
            series.addLast(candle)
            return true
        }
        if (last != null && candle.openedAt.isBefore(last.openedAt)) return false
        series.addLast(candle)
        while (series.size > 800) series.removeFirst()
        return true
    }

    private fun ema(values: List<Double>, period: Int): Double? {
        if (values.size < period) return null
        val alpha = 2.0 / (period + 1.0)
        var value = values.take(period).average()
        for (price in values.drop(period)) value = alpha * price + (1.0 - alpha) * value
        return value
    }

    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size < period + 1) return null
        val slice = candles.takeLast(period + 1)
        val trs = slice.zipWithNext { previous, current ->
            maxOf(
                current.high - current.low,
                kotlin.math.abs(current.high - previous.close),
                kotlin.math.abs(current.low - previous.close),
            )
        }
        return trs.average()
    }

    private fun m15Trend(candles: List<Candle>): String {
        val closes = candles.map { it.close }
        val ema20 = ema(closes, 20) ?: return "neutral"
        val ema50 = ema(closes, 50) ?: return "neutral"
        val last = closes.last()
        return when {
            ema20 < ema50 && last < ema20 -> "bearish"
            ema20 > ema50 && last > ema20 -> "bullish"
            else -> "neutral"
        }
    }
}
