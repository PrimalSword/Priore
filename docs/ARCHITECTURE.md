# Priore Android-only architecture

The MVP intentionally keeps all market monitoring on the Android device.

```text
cTrader JSON/WebSocket :5036
          |
          v
CTraderWebSocketClient
          |
   +------+------+
   |             |
 M5/M15       live quote
   |             |
   v             v
StrategyEngine  dashboard
   |
   +--> WAIT
   +--> BUY_SETUP  --> local Android notification
   +--> SELL_SETUP --> local Android notification
```

## Runtime boundaries

- `PrioreMonitorService`: user-started foreground service and lifecycle owner.
- `CTraderWebSocketClient`: app/account auth, symbol discovery, history, spot/live trendbars, heartbeat and reconnect.
- `StrategyEngine`: deterministic M5/M15 analysis with no Android dependency.
- `CredentialStore`: AES/GCM encryption backed by Android Keystore.
- `TokenRefresher`: access-token renewal using the stored refresh token.
- `PrioreNotifications`: foreground status and actionable local alerts.
- `SignalStore`: non-secret UI/status snapshots.

## Runtime safeguards

- socket callbacks are serialized on one worker;
- stale WebSocket generations cannot spawn duplicate reconnect loops;
- live trendbar subscriptions are idempotent inside each session;
- at quarter-hour boundaries, a newly closed M15 candle is processed before the corresponding M5 signal evaluation;
- only closed, de-duplicated M5/M15 bars reach the strategy engine.

## Build baseline

The MVP targets the stable Android API 36 toolchain, with AndroidX Core 1.17 and Activity 1.11. Using the stable SDK keeps the first physical-device build predictable while the product logic is still being validated.

## Deliberate exclusions

The MVP has no cloud backend, Firebase dependency, remote order execution, boot receiver or automatic trading path. If a future server mode is introduced it should remain a separate transport/runtime layer instead of contaminating the strategy core.
