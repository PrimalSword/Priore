from datetime import datetime, timedelta, timezone

from priore.models import Candle, SignalKind
from priore.strategy import StrategyEngine


def candle(tf: str, i: int, o: float, h: float, l: float, c: float) -> Candle:
    return Candle(tf, datetime(2026, 1, 1, tzinfo=timezone.utc) + timedelta(minutes=i), o, h, l, c, 10)


def test_waits_without_history():
    engine = StrategyEngine()
    plan = engine.evaluate()
    assert plan.kind == SignalKind.WAIT


def test_detects_bearish_rejection_with_bearish_m15():
    engine = StrategyEngine(level_lookback=12, min_rr=2.0)

    # Deliberately declining M15 closes -> EMA20 below EMA50.
    m15 = []
    for i in range(70):
        close = 4500 - i * 1.5
        m15.append(candle("M15", i * 15, close + 0.5, close + 1.0, close - 1.0, close))
    engine.seed("M15", m15)

    m5 = []
    base = 4380.0
    for i in range(30):
        close = base - i * 0.20
        m5.append(candle("M5", i * 5, close + 0.10, close + 0.50, close - 0.50, close))
    engine.seed("M5", m5)

    resistance = max(c.high for c in list(engine.m5)[-12:])
    last = candle("M5", 30 * 5, resistance - 1.0, resistance + 0.50, resistance - 1.8, resistance - 1.2)
    plan = engine.on_closed_candle(last)

    assert plan is not None
    assert plan.kind == SignalKind.SELL_SETUP
    assert plan.stop > plan.entry
    assert plan.target < plan.entry
    assert plan.risk_reward == 2.0
