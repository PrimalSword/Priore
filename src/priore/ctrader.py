from __future__ import annotations

import logging
import time
from collections.abc import Callable
from datetime import datetime, timedelta, timezone

from ctrader_open_api import Client, Protobuf, TcpProtocol
from ctrader_open_api.endpoints import EndPoints
from ctrader_open_api.messages.OpenApiCommonMessages_pb2 import ProtoHeartbeatEvent
from ctrader_open_api.messages.OpenApiMessages_pb2 import (
    ProtoOAAccountAuthReq,
    ProtoOAAccountAuthRes,
    ProtoOAApplicationAuthReq,
    ProtoOAApplicationAuthRes,
    ProtoOAErrorRes,
    ProtoOAGetAccountListByAccessTokenReq,
    ProtoOAGetAccountListByAccessTokenRes,
    ProtoOAGetTrendbarsReq,
    ProtoOAGetTrendbarsRes,
    ProtoOASpotEvent,
    ProtoOASubscribeLiveTrendbarReq,
    ProtoOASubscribeSpotsReq,
    ProtoOASymbolByIdReq,
    ProtoOASymbolByIdRes,
    ProtoOASymbolsListReq,
    ProtoOASymbolsListRes,
)
from ctrader_open_api.messages.OpenApiModelMessages_pb2 import ProtoOATrendbarPeriod
from twisted.internet import reactor

from .config import Settings
from .models import Candle

logger = logging.getLogger(__name__)


class CTraderMarketDataService:
    """Read-only cTrader market-data client. It never sends order messages."""

    _TIMEFRAME_MINUTES = {"M5": 5, "M15": 15}

    def __init__(
        self,
        settings: Settings,
        on_history: Callable[[str, list[Candle]], None],
        on_closed_candle: Callable[[Candle], None],
    ) -> None:
        self.settings = settings
        self.on_history = on_history
        self.on_closed_candle = on_closed_candle
        host = (
            EndPoints.PROTOBUF_LIVE_HOST
            if settings.environment == "live"
            else EndPoints.PROTOBUF_DEMO_HOST
        )
        self.client = Client(host, EndPoints.PROTOBUF_PORT, TcpProtocol)
        self.account_id: int | None = settings.account_id
        self.symbol_id: int | None = None
        self.symbol_digits = 2
        self._history_loaded: set[str] = set()
        self._last_seeded: dict[str, datetime] = {}
        self._last_emitted: dict[str, datetime] = {}
        self._pending_live: dict[str, Candle] = {}
        self._subscribed = False

    def start(self) -> None:
        self.client.setConnectedCallback(self._connected)
        self.client.setDisconnectedCallback(self._disconnected)
        self.client.setMessageReceivedCallback(self._on_message)
        self.client.startService()
        reactor.run()

    def _connected(self, client) -> None:
        logger.info("Connected to cTrader %s endpoint", self.settings.environment)
        req = ProtoOAApplicationAuthReq()
        req.clientId = self.settings.client_id
        req.clientSecret = self.settings.client_secret
        self._send(req)

    @staticmethod
    def _disconnected(client, reason) -> None:
        logger.warning("Disconnected from cTrader: %s", reason)

    def _send(self, request) -> None:
        deferred = self.client.send(request)
        deferred.addErrback(
            lambda failure: logger.error("cTrader request failed: %s", failure)
        )

    def _on_message(self, client, message) -> None:
        payload = message.payloadType
        if payload == ProtoHeartbeatEvent().payloadType:
            return
        if payload == ProtoOAApplicationAuthRes().payloadType:
            self._request_accounts()
            return
        if payload == ProtoOAGetAccountListByAccessTokenRes().payloadType:
            self._handle_accounts(Protobuf.extract(message))
            return
        if payload == ProtoOAAccountAuthRes().payloadType:
            self._request_symbols()
            return
        if payload == ProtoOASymbolsListRes().payloadType:
            self._handle_symbols(Protobuf.extract(message))
            return
        if payload == ProtoOASymbolByIdRes().payloadType:
            self._handle_symbol_details(Protobuf.extract(message))
            return
        if payload == ProtoOAGetTrendbarsRes().payloadType:
            self._handle_history(Protobuf.extract(message))
            return
        if payload == ProtoOASpotEvent().payloadType:
            self._handle_spot(Protobuf.extract(message))
            return
        if payload == ProtoOAErrorRes().payloadType:
            logger.error("cTrader API error: %s", Protobuf.extract(message))

    def _request_accounts(self) -> None:
        req = ProtoOAGetAccountListByAccessTokenReq()
        req.accessToken = self.settings.access_token
        self._send(req)

    def _handle_accounts(self, response) -> None:
        accounts = list(response.ctidTraderAccount)
        if not accounts:
            raise RuntimeError("No cTrader accounts are authorized for this access token")

        if self.account_id is None:
            wanted_live = self.settings.environment == "live"
            matching = [a for a in accounts if bool(a.isLive) == wanted_live]
            chosen = matching[0] if matching else accounts[0]
            self.account_id = int(chosen.ctidTraderAccountId)
        elif not any(int(a.ctidTraderAccountId) == self.account_id for a in accounts):
            raise RuntimeError(
                f"CTRADER_ACCOUNT_ID {self.account_id} is not authorized by this token"
            )

        logger.info("Using cTrader account id %s", self.account_id)
        req = ProtoOAAccountAuthReq()
        req.ctidTraderAccountId = self.account_id
        req.accessToken = self.settings.access_token
        self._send(req)

    def _request_symbols(self) -> None:
        req = ProtoOASymbolsListReq()
        req.ctidTraderAccountId = self._require_account()
        req.includeArchivedSymbols = False
        self._send(req)

    @staticmethod
    def _normalize_symbol(name: str) -> str:
        return "".join(ch for ch in name.upper() if ch.isalnum())

    def _handle_symbols(self, response) -> None:
        target = self._normalize_symbol(self.settings.symbol)
        exact = [
            symbol
            for symbol in response.symbol
            if self._normalize_symbol(symbol.symbolName) == target
        ]
        if not exact:
            candidates = [
                symbol.symbolName
                for symbol in response.symbol
                if "XAU" in symbol.symbolName.upper()
            ]
            raise RuntimeError(
                f"Symbol {self.settings.symbol!r} not found. XAU candidates: {candidates[:20]}"
            )

        selected = exact[0]
        self.symbol_id = int(selected.symbolId)
        logger.info("Resolved %s to symbolId=%s", selected.symbolName, self.symbol_id)

        req = ProtoOASymbolByIdReq()
        req.ctidTraderAccountId = self._require_account()
        req.symbolId.append(self.symbol_id)
        self._send(req)

    def _handle_symbol_details(self, response) -> None:
        if not response.symbol:
            raise RuntimeError("cTrader returned no full symbol details")
        symbol = response.symbol[0]
        self.symbol_digits = int(symbol.digits)
        logger.info("Symbol precision: %s digits", self.symbol_digits)
        self._request_history("M5", self.settings.m5_history_count)
        self._request_history("M15", self.settings.m15_history_count)

    def _request_history(self, timeframe: str, count: int) -> None:
        period = ProtoOATrendbarPeriod.Value(timeframe)
        now_ms = int(time.time() * 1000)
        minutes = self._TIMEFRAME_MINUTES[timeframe]
        # Wider than count to tolerate market closures/weekends; count caps the response.
        from_ms = now_ms - count * minutes * 60_000 * 4
        req = ProtoOAGetTrendbarsReq()
        req.ctidTraderAccountId = self._require_account()
        req.symbolId = self._require_symbol()
        req.period = period
        req.fromTimestamp = max(0, from_ms)
        req.toTimestamp = now_ms
        req.count = count
        self._send(req)

    def _handle_history(self, response) -> None:
        timeframe = ProtoOATrendbarPeriod.Name(response.period)
        now = datetime.now(timezone.utc)
        candles = sorted(
            (
                Candle.from_ctrader_trendbar(bar, timeframe, self.symbol_digits)
                for bar in response.trendbar
            ),
            key=lambda candle: candle.opened_at,
        )
        closed = [candle for candle in candles if self._is_closed(candle, now)]
        self.on_history(timeframe, closed)
        if closed:
            self._last_seeded[timeframe] = closed[-1].opened_at
            self._last_emitted[timeframe] = closed[-1].opened_at
        self._history_loaded.add(timeframe)
        logger.info("Loaded %s closed historical %s candles", len(closed), timeframe)

        if {"M5", "M15"}.issubset(self._history_loaded) and not self._subscribed:
            self._subscribe_live()

    def _subscribe_live(self) -> None:
        # cTrader requires spot subscription before live trendbar subscription.
        spot = ProtoOASubscribeSpotsReq()
        spot.ctidTraderAccountId = self._require_account()
        spot.symbolId.append(self._require_symbol())
        spot.subscribeToSpotTimestamp = True
        self._send(spot)

        for timeframe in ("M5", "M15"):
            req = ProtoOASubscribeLiveTrendbarReq()
            req.ctidTraderAccountId = self._require_account()
            req.symbolId = self._require_symbol()
            req.period = ProtoOATrendbarPeriod.Value(timeframe)
            self._send(req)
        self._subscribed = True
        logger.info("Subscribed to XAU spot plus M5/M15 live trendbars")

    def _handle_spot(self, event) -> None:
        if int(event.symbolId) != self._require_symbol():
            return

        now = datetime.now(timezone.utc)
        for bar in event.trendbar:
            timeframe = ProtoOATrendbarPeriod.Name(bar.period)
            if timeframe not in self._TIMEFRAME_MINUTES:
                continue
            candle = Candle.from_ctrader_trendbar(bar, timeframe, self.symbol_digits)
            self._process_live_candle(candle, now)

    def _process_live_candle(self, candle: Candle, now: datetime) -> None:
        timeframe = candle.timeframe
        pending = self._pending_live.get(timeframe)

        if pending and pending.opened_at < candle.opened_at:
            if self._is_closed(pending, now):
                self._emit_closed(pending)
            self._pending_live.pop(timeframe, None)

        if self._is_closed(candle, now):
            self._emit_closed(candle)
        else:
            self._pending_live[timeframe] = candle

    def _emit_closed(self, candle: Candle) -> None:
        last = self._last_emitted.get(candle.timeframe)
        if last is not None and candle.opened_at <= last:
            return
        self._last_emitted[candle.timeframe] = candle.opened_at
        self.on_closed_candle(candle)

    def _is_closed(self, candle: Candle, now: datetime) -> bool:
        minutes = self._TIMEFRAME_MINUTES[candle.timeframe]
        return now >= candle.opened_at + timedelta(minutes=minutes)

    def _require_account(self) -> int:
        if self.account_id is None:
            raise RuntimeError("Account not authenticated yet")
        return self.account_id

    def _require_symbol(self) -> int:
        if self.symbol_id is None:
            raise RuntimeError("Symbol not resolved yet")
        return self.symbol_id
