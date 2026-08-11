# Priore

Priore is a **read-only XAUUSD market assistant** for cTrader Open API. The first MVP watches M5 and M15, evaluates only confirmed candle data, and emits `WAIT`, `BUY_SETUP`, or `SELL_SETUP` decisions. It does **not** create, modify, or close trades.

## Security first

Never commit cTrader credentials or tokens. The repository ignores `.env` and runtime state by default. Keep these values outside Git:

- `CTRADER_CLIENT_ID`
- `CTRADER_CLIENT_SECRET`
- `CTRADER_ACCESS_TOKEN`
- `CTRADER_REFRESH_TOKEN`

If any secret has ever been committed to another repository, rotate it before using Priore.

## Current data flow

```text
cTrader demo endpoint
        |
        v
Application auth -> Account auth -> Symbol discovery
        |
        v
XAUUSD historical M5/M15
        |
        v
Spot subscription -> Live M5/M15 trendbars
        |
        v
StrategyEngine
        |
        +--> WAIT (logs)
        +--> BUY_SETUP / SELL_SETUP (logs + optional Telegram)
```

The app follows the cTrader requirement to subscribe to spot events before subscribing to live trendbars.

## Strategy v0.1

This is intentionally conservative and deterministic:

- M15 trend: EMA20/EMA50 regime filter.
- M5 volatility: ATR(14).
- M5 structure: rolling support/resistance.
- SELL setup: bearish M15 plus bearish rejection or confirmed support break.
- BUY setup: bullish M15 plus bullish rejection or confirmed resistance break.
- Minimum target uses a configurable risk/reward ratio.
- A setup is informational; Priore never sends order messages.

The rules are a baseline for validation, not a claim of profitability.

## Local setup

Python 3.11+ is recommended.

```bash
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows PowerShell
# .venv\Scripts\Activate.ps1

pip install -e '.[dev]'
cp .env.example .env
```

Fill `.env` locally with your credentials and existing access/refresh tokens. Start with:

```env
CTRADER_ENV=demo
CTRADER_SYMBOL=XAUUSD
```

If the token authorizes more than one demo account, set `CTRADER_ACCOUNT_ID`. Otherwise Priore selects the first account for the configured environment.

Run:

```bash
priore
```

## Optional Telegram alerts

Create a Telegram bot and add these only to your local/deployment secret store:

```env
TELEGRAM_BOT_TOKEN=
TELEGRAM_CHAT_ID=
```

`WAIT` decisions stay in logs. Telegram only receives `BUY_SETUP` and `SELL_SETUP`.

## Tests

```bash
pytest -q
ruff check src tests
```

GitHub Actions runs the same checks without any trading credentials.

## Roadmap

1. Validate connectivity against the authorized cTrader demo account.
2. Verify broker-specific XAUUSD symbol resolution and candle precision.
3. Persist candle/signal history for audit and backtesting.
4. Add session/news risk gates.
5. Calibrate strategy thresholds from historical results.
6. Add a small dashboard/health endpoint.
7. Only after a substantial demo validation phase, consider a separate trading execution service. It should not share the read-only MVP's permissions or code path.

## Important

Priore is an analytical assistant. Markets are risky, especially leveraged XAUUSD. No strategy guarantees profit.
