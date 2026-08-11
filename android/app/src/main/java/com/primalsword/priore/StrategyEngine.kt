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
            return TradePlan(
                SignalKind.WAIT,
                "Aguardando histórico suficiente para calcular contexto e níveis.",
                nextTrigger = "O Priore começará a classificar o cenário quando M5 e M15 estiverem aquecidos.",
            )
        }

        val current = bars5.last()
        val window = bars5.dropLast(1).takeLast(levelLookback)
        val support = window.minOf { it.low }
        val resistance = window.maxOf { it.high }
        val atr = atr(bars5, atrPeriod)
            ?: return TradePlan(
                SignalKind.WAIT,
                "ATR indisponível.",
                nextTrigger = "Aguardar novas velas M5 para estabilizar a volatilidade.",
            )
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
                reason = if (bearishRejection) {
                    "M15 baixista e rejeição vendedora confirmada na resistência no fechamento M5."
                } else {
                    "M15 baixista e perda de suporte confirmada no fechamento M5."
                },
                nextTrigger = "Setup confirmado. A leitura só permanece válida enquanto a estrutura vendedora não for invalidada.",
                invalidation = "Fechamento M5 acima da região de stop técnico ${fmt(stop)} invalida esta leitura.",
                confirmationPrice = entry,
                invalidationPrice = stop,
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
                reason = if (bullishRejection) {
                    "M15 altista e defesa compradora confirmada no suporte no fechamento M5."
                } else {
                    "M15 altista e rompimento de resistência confirmado no fechamento M5."
                },
                nextTrigger = "Setup confirmado. A leitura só permanece válida enquanto a estrutura compradora não for invalidada.",
                invalidation = "Fechamento M5 abaixo da região de stop técnico ${fmt(stop)} invalida esta leitura.",
                confirmationPrice = entry,
                invalidationPrice = stop,
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

        val nearSupport = current.close <= support + 0.45 * atr &&
            current.close >= support - 0.20 * atr
        val nearResistance = current.close >= resistance - 0.45 * atr &&
            current.close <= resistance + 0.20 * atr

        if (trend == "bullish" && nearSupport) {
            val confirm = support + 0.12 * atr
            val invalidate = support - 0.20 * atr
            return TradePlan(
                kind = SignalKind.WATCH_BUY,
                reason = "M15 altista e preço testando a zona de suporte. Há contexto comprador, mas ainda falta confirmação no M5.",
                nextTrigger = "Buscar fechamento M5 defendendo ${fmt(support)} com rejeição inferior; alternativamente, rompimento confirmado acima de ${fmt(resistance + 0.08 * atr)}.",
                invalidation = "Perda limpa abaixo de ${fmt(invalidate)} enfraquece a tese compradora e exige reavaliação.",
                confirmationPrice = confirm,
                invalidationPrice = invalidate,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        if (trend == "bullish" && nearResistance) {
            val confirm = resistance + 0.08 * atr
            val invalidate = resistance - 0.25 * atr
            return TradePlan(
                kind = SignalKind.WATCH_BUY,
                reason = "M15 altista e preço encostando na resistência. O Priore está observando possível continuação por rompimento.",
                nextTrigger = "Fechamento M5 acima de ${fmt(confirm)} confirma o rompimento comprador.",
                invalidation = "Rejeição forte da resistência e retorno abaixo de ${fmt(invalidate)} cancela o watch de rompimento.",
                confirmationPrice = confirm,
                invalidationPrice = invalidate,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        if (trend == "bearish" && nearResistance) {
            val confirm = resistance - 0.12 * atr
            val invalidate = resistance + 0.20 * atr
            return TradePlan(
                kind = SignalKind.WATCH_SELL,
                reason = "M15 baixista e preço testando a zona de resistência. Há contexto vendedor, mas ainda falta rejeição confirmada no M5.",
                nextTrigger = "Buscar fechamento M5 rejeitando ${fmt(resistance)} com pavio superior; alternativamente, perda confirmada abaixo de ${fmt(support - 0.08 * atr)}.",
                invalidation = "Rompimento limpo acima de ${fmt(invalidate)} enfraquece a tese vendedora e exige reavaliação.",
                confirmationPrice = confirm,
                invalidationPrice = invalidate,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        if (trend == "bearish" && nearSupport) {
            val confirm = support - 0.08 * atr
            val invalidate = support + 0.25 * atr
            return TradePlan(
                kind = SignalKind.WATCH_SELL,
                reason = "M15 baixista e preço pressionando o suporte. O Priore está observando possível continuação por rompimento.",
                nextTrigger = "Fechamento M5 abaixo de ${fmt(confirm)} confirma a perda do suporte.",
                invalidation = "Defesa forte do suporte e recuperação acima de ${fmt(invalidate)} cancela o watch vendedor.",
                confirmationPrice = confirm,
                invalidationPrice = invalidate,
                trendM15 = trend,
                support = support,
                resistance = resistance,
                atrM5 = atr,
            )
        }

        val neutralAtLevel = trend == "neutral" && (nearSupport || nearResistance)
        val reason = when {
            neutralAtLevel && nearSupport ->
                "M15 neutro e preço sobre o suporte. A região é relevante, mas ainda não existe vantagem direcional suficiente."
            neutralAtLevel && nearResistance ->
                "M15 neutro e preço sobre a resistência. A região é relevante, mas ainda não existe vantagem direcional suficiente."
            trend == "bullish" ->
                "M15 altista, porém o preço está no meio da faixa M5. Entrar agora reduziria a qualidade do risco/retorno."
            trend == "bearish" ->
                "M15 baixista, porém o preço está no meio da faixa M5. Entrar agora reduziria a qualidade do risco/retorno."
            else ->
                "M15 neutro e sem confirmação suficiente no fechamento M5."
        }
        val nextTrigger = when (trend) {
            "bullish" -> "Aguardar aproximação do suporte ${fmt(support)} para defesa ou rompimento confirmado da resistência ${fmt(resistance)}."
            "bearish" -> "Aguardar aproximação da resistência ${fmt(resistance)} para rejeição ou perda confirmada do suporte ${fmt(support)}."
            else -> "Aguardar o M15 recuperar direção e o M5 confirmar defesa ou rompimento em um dos níveis da faixa."
        }

        return TradePlan(
            kind = SignalKind.WAIT,
            reason = reason,
            nextTrigger = nextTrigger,
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

    private fun fmt(value: Double): String = "%.2f".format(java.util.Locale.US, value)
}
