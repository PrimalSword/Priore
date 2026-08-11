from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum


class SignalKind(str, Enum):
    WAIT = "WAIT"
    BUY_SETUP = "BUY_SETUP"
    SELL_SETUP = "SELL_SETUP"


@dataclass(frozen=True, slots=True)
class Candle:
    timeframe: str
    opened_at: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int = 0

    @property
    def body(self) -> float:
        return abs(self.close - self.open)

    @property
    def upper_wick(self) -> float:
        return max(0.0, self.high - max(self.open, self.close))

    @property
    def lower_wick(self) -> float:
        return max(0.0, min(self.open, self.close) - self.low)

    @classmethod
    def from_ctrader_trendbar(cls, trendbar, timeframe: str, digits: int) -> "Candle":
        divisor = 100000.0
        low = trendbar.low / divisor
        open_ = (trendbar.low + int(trendbar.deltaOpen)) / divisor
        close = (trendbar.low + int(trendbar.deltaClose)) / divisor
        high = (trendbar.low + int(trendbar.deltaHigh)) / divisor
        opened_at = datetime.fromtimestamp(
            int(trendbar.utcTimestampInMinutes) * 60,
            tz=timezone.utc,
        )
        return cls(
            timeframe=timeframe,
            opened_at=opened_at,
            open=round(open_, digits),
            high=round(high, digits),
            low=round(low, digits),
            close=round(close, digits),
            volume=int(trendbar.volume),
        )


@dataclass(frozen=True, slots=True)
class TradePlan:
    kind: SignalKind
    reason: str
    entry: float | None = None
    stop: float | None = None
    target: float | None = None
    risk_reward: float | None = None
    trend_m15: str = "neutral"
    support: float | None = None
    resistance: float | None = None
    atr_m5: float | None = None
