from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone

import firebase_admin
from firebase_admin import credentials, messaging

from .models import SignalKind, TradePlan

logger = logging.getLogger(__name__)


class AlertSink:
    def __init__(self, firebase_topic: str) -> None:
        self.firebase_topic = firebase_topic
        self.firebase_enabled = self._initialize_firebase()

    def publish(self, plan: TradePlan) -> None:
        message = self.format(plan)
        logger.info("%s", message.replace("\n", " | "))
        if plan.kind != SignalKind.WAIT and self.firebase_enabled:
            self._send_fcm(plan)

    @staticmethod
    def format(plan: TradePlan) -> str:
        lines = [f"PRIORE | {plan.kind.value}", plan.reason, f"M15: {plan.trend_m15}"]
        if plan.support is not None:
            lines.append(f"Suporte: {plan.support:.2f}")
        if plan.resistance is not None:
            lines.append(f"Resistência: {plan.resistance:.2f}")
        if plan.atr_m5 is not None:
            lines.append(f"ATR M5: {plan.atr_m5:.2f}")
        if plan.entry is not None:
            lines.extend(
                [
                    f"Entrada indicativa: {plan.entry:.2f}",
                    f"Stop técnico: {plan.stop:.2f}",
                    f"Alvo técnico: {plan.target:.2f}",
                    f"R:R mínimo: {plan.risk_reward:.2f}",
                ]
            )
        lines.append("Leitura técnica; não executa ordens.")
        return "\n".join(lines)

    def _initialize_firebase(self) -> bool:
        try:
            firebase_admin.get_app()
            return True
        except ValueError:
            pass

        try:
            raw_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
            if raw_json:
                firebase_admin.initialize_app(credentials.Certificate(json.loads(raw_json)))
            else:
                # Uses GOOGLE_APPLICATION_CREDENTIALS or the hosting platform's ADC.
                firebase_admin.initialize_app()
            logger.info("Firebase Admin initialized; topic=%s", self.firebase_topic)
            return True
        except Exception as exc:  # noqa: BLE001 - startup must remain usable without push.
            logger.warning("Firebase push disabled: %s", exc)
            return False

    def _send_fcm(self, plan: TradePlan) -> None:
        def number(value: float | None) -> str:
            return "" if value is None else f"{value:.2f}"

        payload = {
            "kind": plan.kind.value,
            "reason": plan.reason,
            "entry": number(plan.entry),
            "stop": number(plan.stop),
            "target": number(plan.target),
            "trendM15": plan.trend_m15,
            "support": number(plan.support),
            "resistance": number(plan.resistance),
            "atrM5": number(plan.atr_m5),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        message = messaging.Message(
            data=payload,
            topic=self.firebase_topic,
            android=messaging.AndroidConfig(priority="high"),
        )
        message_id = messaging.send(message)
        logger.info("FCM signal sent: %s", message_id)
