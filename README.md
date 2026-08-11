# Priore

Priore is an **Android XAUUSD market assistant** backed by a read-only cTrader Open API monitor.

The backend watches M5 and M15, evaluates confirmed candle closes, and emits `WAIT`, `BUY_SETUP`, or `SELL_SETUP`. Actionable setups are pushed to the Android app through Firebase Cloud Messaging (FCM). Priore does **not** create, modify, or close trades in this MVP.

## Architecture

```text
cTrader Open API (demo)
        |
        v
Priore backend (Python)
- application/account auth
- XAUUSD M5 + M15
- closed-candle strategy
- WAIT / BUY_SETUP / SELL_SETUP
        |
        v
Firebase Cloud Messaging
        |
        v
Priore Android
- native push notification
- last-signal dashboard
```

This architecture is intentional. cTrader secrets and long-lived tokens stay on the backend instead of being embedded in the Android APK, and Android does not need to keep a permanent market-data process alive in the background.

## Repository layout

```text
android/                 Native Android app
src/priore/              cTrader analysis backend
tests/                   Backend strategy tests
.env.example             Backend environment template
```

## Security

Never commit:

- `CTRADER_CLIENT_SECRET`
- `CTRADER_ACCESS_TOKEN`
- `CTRADER_REFRESH_TOKEN`
- Firebase Admin service-account JSON
- `.env`
- `android/local.properties`
- signing keystores

The Android app receives only analytical signal data. It does not receive cTrader credentials.

## Backend setup

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

Fill `.env` locally with the cTrader demo credentials/tokens and Firebase Admin authentication. Keep:

```env
CTRADER_ENV=demo
CTRADER_SYMBOL=XAUUSD
FIREBASE_TOPIC=priore-xau
```

For Firebase Admin, use one of these approaches:

1. Application Default Credentials on Google Cloud.
2. `GOOGLE_APPLICATION_CREDENTIALS` pointing to a private service-account JSON file.
3. `FIREBASE_SERVICE_ACCOUNT_JSON` in the deployment secret store.

Run:

```bash
priore
```

The backend seeds historical M5/M15 candles, subscribes to XAUUSD spot/live trendbars, evaluates only closed M5 candles, and sends FCM only for actionable setups. `WAIT` remains in logs.

## Android setup

The Android project targets API 37 and uses Firebase Cloud Messaging. Open the `android/` directory in Android Studio.

Copy:

```text
android/local.properties.example
```

to:

```text
android/local.properties
```

Then add the Firebase Android app's public client configuration:

```properties
FIREBASE_APP_ID=
FIREBASE_API_KEY=
FIREBASE_PROJECT_ID=
FIREBASE_SENDER_ID=
FIREBASE_TOPIC=priore-xau
```

These values identify the Firebase Android client. **Do not put cTrader secrets or Firebase Admin private credentials in the Android project.**

The app initializes Firebase, subscribes to `priore-xau`, requests Android notification permission when required, receives high-priority FCM data messages, persists the latest signal locally, and displays a native notification.

Because binary Gradle wrapper files are not generated through the GitHub connector, if the checkout has no wrapper yet, open `android/` with Android Studio or run the installed Gradle once to generate it:

```bash
gradle wrapper --gradle-version 9.5.0
```

Then build with:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Strategy v0.1

- M15 trend: EMA20/EMA50 regime filter.
- M5 volatility: ATR(14).
- M5 structure: rolling support/resistance.
- SELL setup: bearish M15 plus bearish rejection or confirmed support break.
- BUY setup: bullish M15 plus bullish rejection or confirmed resistance break.
- Minimum target uses a configurable risk/reward ratio.
- Decisions are generated on confirmed M5 closes.

These rules are a validation baseline, not a claim of profitability.

## Tests

```bash
pytest -q
ruff check src tests
```

## Next milestones

1. Connect the real authorized cTrader demo account.
2. Create/configure the Firebase project and Android app.
3. Validate end-to-end FCM delivery on the physical Android phone.
4. Persist signal history and outcome metrics.
5. Add session and high-impact-news risk gates.
6. Add backend health/status to the Android dashboard.
7. Backtest and calibrate the strategy before any discussion of execution automation.

## Important

Priore is an analytical assistant. XAUUSD is volatile and leveraged trading can produce rapid losses. The current application is deliberately read-only and does not execute orders.
