from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field

from .models import Candle, SignalKind, TradePlan


def _ema(values: list[float], period: int) -> float | None:
    if len(values) < period:
        return None
    alpha = 2.0 / (period + 1.0)
    value = sum(values[:period]) / period
    for price in values[period:]:
        value = alpha * price + (1.0 - alpha) * value
    return value


def _atr(candles: list[Candle], period: int) -> float | None:
    if len(candles) < period + 1:
        return None
    trs: list[float] = []
    for previous, current in zip(candles[-(period + 1) : -1], candles[-period:]):
        trs.append(
            max(
                current.high - current.low,
                abs(current.high - previous.close),
                abs(current.low - previous.close),
            )
        )
    return sum(trs) / len(trs)


@dataclass(slots=True)
class StrategyEngine:
    atr_period: int = 14
    level_lookback: int = 12
    min_rr: float = 1.8
    m5: deque[Candle] = field(init=False)
    m15: deque[Candle] = field(init=False)

    def __post_init__(self) -> None:
        self.m5 = deque(maxlen=800)
        self.m15 = deque(maxlen=800)

    def seed(self, timeframe: str, candles: list[Candle]) -> None:
        target = self._series(timeframe)
        target.clear()
        for candle in sorted(candles, key=lambda x: x.opened_at):
            self._upsert(target, candle)

    def on_closed_candle(self, candle: Candle) -> TradePlan | None:
        target = self._series(candle.timeframe)
        changed = self._upsert(target, candle)
        if not changed or candle.timeframe != "M5":
            return None
        return self.evaluate()

    def evaluate(self) -> TradePlan:
        m5 = list(self.m5)
        m15 = list(self.m15)
        if len(m5) < max(self.atr_period + 2, self.level_lookback + 2) or len(m15) < 50:
            return TradePlan(SignalKind.WAIT, "Aguardando histórico suficiente.")

        current = m5[-1]
        previous_window = m5[-(self.level_lookback + 1) : -1]
        support = min(c.low for c in previous_window)
        resistance = max(c.high for c in previous_window)
        atr = _atr(m5, self.atr_period)
        if not atr or atr <= 0:
            return TradePlan(SignalKind.WAIT, "ATR indisponível.")

        trend = self._m15_trend(m15)
        tolerance = 0.18 * atr
        body = max(current.body, atr * 0.03)

        bearish_rejection = (
            current.high >= resistance - tolerance
            and current.close < resistance - 0.12 * atr
            and current.upper_wick >= body * 1.15
        )
        bullish_rejection = (
            current.low <= support + tolerance
            and current.close > support + 0.12 * atr
            and current.lower_wick >= body * 1.15
        )
        breakdown = current.close < support - 0.08 * atr
        breakout = current.close > resistance + 0.08 * atr

        if trend == "bearish" and (bearish_rejection or breakdown):
            entry = current.close
            stop = max(current.high, resistance) + 0.25 * atr
            risk = stop - entry
            target = entry - self.min_rr * risk
            return TradePlan(
                SignalKind.SELL_SETUP,
                "M15 baixista com rejeição/rompimento vendedor confirmado no fechamento M5.",
                entry=entry,
                stop=stop,
                target=target,
                risk_reward=self.min_rr,
                trend_m15=trend,
                support=support,
                resistance=resistance,
                atr_m5=atr,
            )

        if trend == "bullish" and (bullish_rejection or breakout):
            entry = current.close
            stop = min(current.low, support) - 0.25 * atr
            risk = entry - stop
            target = entry + self.min_rr * risk
            return TradePlan(
                SignalKind.BUY_SETUP,
                "M15 altista com rejeição/rompimento comprador confirmado no fechamento M5.",
                entry=entry,
                stop=stop,
                target=target,
                risk_reward=self.min_rr,
                trend_m15=trend,
                support=support,
                resistance=resistance,
                atr_m5=atr,
            )

        return TradePlan(
            SignalKind.WAIT,
            "Sem confirmação suficiente no fechamento M5.",
            trend_m15=trend,
            support=support,
            resistance=resistance,
            atr_m5=atr,
        )

    @staticmethod
    def _upsert(series: deque[Candle], candle: Candle) -> bool:
        if series and series[-1].opened_at == candle.opened_at:
            if series[-1] == candle:
                return False
            series[-1] = candle
            return True
        if series and candle.opened_at < series[-1].opened_at:
            return False
        series.append(candle)
        return True

    def _series(self, timeframe: str) -> deque[Candle]:
        key = timeframe.upper()
        if key == "M5":
            return self.m5
        if key == "M15":
            return self.m15
        raise ValueError(f"Unsupported timeframe: {timeframe}")

    @staticmethod
    def _m15_trend(candles: list[Candle]) -> str:
        closes = [c.close for c in candles]
        ema20 = _ema(closes, 20)
        ema50 = _ema(closes, 50)
        if ema20 is None or ema50 is None:
            return "neutral"
        last = closes[-1]
        if ema20 < ema50 and last < ema20:
            return "bearish"
        if ema20 > ema50 and last > ema20:
            return "bullish"
        return "neutral"
