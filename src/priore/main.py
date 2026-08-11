from __future__ import annotations

import logging

from .alerts import AlertSink
from .config import Settings
from .ctrader import CTraderMarketDataService
from .models import Candle, SignalKind
from .strategy import StrategyEngine


def run() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s | %(message)s",
    )
    settings = Settings.from_env()
    engine = StrategyEngine(
        atr_period=settings.atr_period,
        level_lookback=settings.level_lookback,
        min_rr=settings.min_rr,
    )
    alerts = AlertSink(settings.firebase_topic)

    def on_history(timeframe: str, candles: list[Candle]) -> None:
        engine.seed(timeframe, candles)
        logging.getLogger(__name__).info(
            "Seeded strategy with %s closed %s candles", len(candles), timeframe
        )

    def on_closed_candle(candle: Candle) -> None:
        plan = engine.on_closed_candle(candle)
        if plan is None:
            return
        logging.getLogger(__name__).info(
            "%s closed %.2f @ %s",
            candle.timeframe,
            candle.close,
            candle.opened_at.isoformat(),
        )
        alerts.publish(plan)
        if plan.kind != SignalKind.WAIT:
            logging.getLogger(__name__).warning(
                "Actionable setup detected: %s", plan.kind.value
            )

    service = CTraderMarketDataService(
        settings=settings,
        on_history=on_history,
        on_closed_candle=on_closed_candle,
    )
    service.start()


if __name__ == "__main__":
    run()
