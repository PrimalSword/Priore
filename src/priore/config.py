from __future__ import annotations

import os
from dataclasses import dataclass

from dotenv import load_dotenv


@dataclass(frozen=True, slots=True)
class Settings:
    client_id: str
    client_secret: str
    access_token: str
    refresh_token: str | None
    environment: str
    account_id: int | None
    symbol: str
    m5_history_count: int
    m15_history_count: int
    atr_period: int
    level_lookback: int
    min_rr: float
    telegram_bot_token: str | None
    telegram_chat_id: str | None

    @classmethod
    def from_env(cls) -> "Settings":
        load_dotenv()

        def required(name: str) -> str:
            value = os.getenv(name, "").strip()
            if not value:
                raise RuntimeError(f"Missing required environment variable: {name}")
            return value

        environment = os.getenv("CTRADER_ENV", "demo").strip().lower()
        if environment not in {"demo", "live"}:
            raise RuntimeError("CTRADER_ENV must be 'demo' or 'live'")

        account_raw = os.getenv("CTRADER_ACCOUNT_ID", "").strip()
        return cls(
            client_id=required("CTRADER_CLIENT_ID"),
            client_secret=required("CTRADER_CLIENT_SECRET"),
            access_token=required("CTRADER_ACCESS_TOKEN"),
            refresh_token=os.getenv("CTRADER_REFRESH_TOKEN") or None,
            environment=environment,
            account_id=int(account_raw) if account_raw else None,
            symbol=os.getenv("CTRADER_SYMBOL", "XAUUSD").strip().upper(),
            m5_history_count=int(os.getenv("M5_HISTORY_COUNT", "300")),
            m15_history_count=int(os.getenv("M15_HISTORY_COUNT", "300")),
            atr_period=int(os.getenv("ATR_PERIOD", "14")),
            level_lookback=int(os.getenv("LEVEL_LOOKBACK", "12")),
            min_rr=float(os.getenv("MIN_RR", "1.8")),
            telegram_bot_token=os.getenv("TELEGRAM_BOT_TOKEN") or None,
            telegram_chat_id=os.getenv("TELEGRAM_CHAT_ID") or None,
        )
