from __future__ import annotations

import logging

import requests

from .models import SignalKind, TradePlan

logger = logging.getLogger(__name__)


class AlertSink:
    def __init__(self, telegram_bot_token: str | None = None, telegram_chat_id: str | None = None):
        self.bot_token = telegram_bot_token
        self.chat_id = telegram_chat_id

    def publish(self, plan: TradePlan) -> None:
        message = self.format(plan)
        logger.info("%s", message.replace("\n", " | "))
        if plan.kind != SignalKind.WAIT and self.bot_token and self.chat_id:
            self._send_telegram(message)

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

    def _send_telegram(self, text: str) -> None:
        url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
        response = requests.post(
            url,
            json={"chat_id": self.chat_id, "text": text, "disable_web_page_preview": True},
            timeout=10,
        )
        response.raise_for_status()
