package com.primalsword.priore

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

class CTraderWebSocketClient(
    private var credentials: CTraderCredentials,
    private val symbolName: String = "XAUUSD",
    private val listener: Listener,
) {
    interface Listener {
        fun onState(state: String)
        fun onPrice(price: Double)
        fun onHistory(timeframe: String, candles: List<Candle>)
        fun onClosedCandle(candle: Candle)
        fun onDemoExecution(event: DemoExecutionEvent)
        fun onAuthExpired(reason: String)
        fun onError(message: String)
    }

    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val http = OkHttpClient.Builder().build()
    private var socket: WebSocket? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var reconnect: ScheduledFuture<*>? = null

    @Volatile
    private var manuallyClosed = false

    private var generation = 0L
    private var accountId: Long? = null
    private var symbolId: Long? = null
    private var digits: Int = 2
    private var minVolume: Long? = null
    private var stepVolume: Long? = null
    private var spotReady = false
    private var liveSubscribed = false
    private val historyReady = mutableSetOf<String>()
    private val historyRequest = mutableMapOf<String, String>()
    private val lastClosedMinute = mutableMapOf<String, Long>()

    fun connect() {
        dispatch {
            manuallyClosed = false
            openSocket()
        }
    }

    fun replaceCredentials(value: CTraderCredentials) {
        dispatch {
            credentials = value
            reconnect?.cancel(true)
            heartbeat?.cancel(true)
            socket?.cancel()
            openSocket()
        }
    }

    fun close() {
        manuallyClosed = true
        reconnect?.cancel(true)
        heartbeat?.cancel(true)
        socket?.close(1000, "Priore stopped")
        worker.shutdownNow()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    fun placeDemoMarketOrder(plan: TradePlan, setupId: String) {
        dispatch {
            if (credentials.environment != "demo") {
                listener.onError("BLOQUEIO DE SEGURANÇA: autoexecução do Priore só existe em DEMO.")
                return@dispatch
            }
            if (plan.kind != SignalKind.BUY_SETUP && plan.kind != SignalKind.SELL_SETUP) return@dispatch
            val entry = plan.entry
            val stop = plan.stop
            val target = plan.target
            if (entry == null || stop == null || target == null) {
                listener.onError("Setup sem entrada/stop/alvo; ordem DEMO não enviada.")
                return@dispatch
            }
            val account = accountId
            val symbol = symbolId
            val volume = minVolume
            if (account == null || symbol == null || volume == null) {
                listener.onError("cTrader ainda não está pronta para enviar ordem DEMO.")
                return@dispatch
            }

            val slDistance = abs(stop - entry)
            val tpDistance = abs(target - entry)
            val relativeSl = (slDistance * 100000.0).roundToLong().coerceAtLeast(1L)
            val relativeTp = (tpDistance * 100000.0).roundToLong().coerceAtLeast(1L)
            val side = if (plan.kind == SignalKind.BUY_SETUP) TRADE_SIDE_BUY else TRADE_SIDE_SELL

            listener.onState("Setup confirmado · enviando ordem DEMO mínima à cTrader…")
            send(
                NEW_ORDER_REQ,
                JSONObject()
                    .put("ctidTraderAccountId", account)
                    .put("symbolId", symbol)
                    .put("orderType", ORDER_TYPE_MARKET)
                    .put("tradeSide", side)
                    .put("volume", volume)
                    .put("relativeStopLoss", relativeSl)
                    .put("relativeTakeProfit", relativeTp)
                    .put("label", DEMO_LABEL)
                    .put("comment", "Priore demo $setupId")
                    .put("clientOrderId", setupId.take(50)),
                clientMsgId = "demo-order-$setupId",
            )
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (worker.isShutdown) return
        try {
            worker.execute(block)
        } catch (_: RejectedExecutionException) {
            // Monitor already shutting down.
        }
    }

    private fun openSocket() {
        if (manuallyClosed) return
        resetSessionState()
        reconnect = null
        val myGeneration = ++generation
        val host = if (credentials.environment == "live") {
            "live.ctraderapi.com"
        } else {
            "demo.ctraderapi.com"
        }
        val request = Request.Builder().url("wss://$host:5036/").build()
        listener.onState("Conectando à cTrader (${credentials.environment})…")

        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                dispatch {
                    if (!isCurrent(myGeneration)) return@dispatch
                    reconnect = null
                    listener.onState("Socket conectado · autenticando aplicação…")
                    send(
                        APP_AUTH_REQ,
                        JSONObject()
                            .put("clientId", credentials.clientId)
                            .put("clientSecret", credentials.clientSecret),
                    )
                    startHeartbeat(myGeneration)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatch {
                    if (isCurrent(myGeneration)) handleMessage(text)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dispatch {
                    if (!isCurrent(myGeneration)) return@dispatch
                    heartbeat?.cancel(true)
                    listener.onError(
                        "Conexão cTrader interrompida: ${t.message ?: "erro de rede"}",
                    )
                    scheduleReconnect(myGeneration)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dispatch {
                    if (!isCurrent(myGeneration)) return@dispatch
                    heartbeat?.cancel(true)
                    scheduleReconnect(myGeneration)
                }
            }
        })
    }

    private fun isCurrent(candidate: Long): Boolean = !manuallyClosed && candidate == generation

    private fun scheduleReconnect(sourceGeneration: Long) {
        if (!isCurrent(sourceGeneration) || reconnect?.isDone == false) return
        listener.onState("Desconectado · tentando novamente em 5 s…")
        reconnect = worker.schedule(
            {
                if (isCurrent(sourceGeneration)) openSocket()
            },
            5,
            TimeUnit.SECONDS,
        )
    }

    private fun startHeartbeat(sourceGeneration: Long) {
        heartbeat?.cancel(true)
        heartbeat = worker.scheduleAtFixedRate(
            {
                if (isCurrent(sourceGeneration)) send(HEARTBEAT, JSONObject())
            },
            10,
            10,
            TimeUnit.SECONDS,
        )
    }

    private fun handleMessage(text: String) {
        val envelope = runCatching { JSONObject(text) }.getOrElse {
            listener.onError("Mensagem inválida recebida da cTrader")
            return
        }
        val type = envelope.optInt("payloadType", -1)
        val payload = envelope.optJSONObject("payload") ?: JSONObject()
        val clientMsgId = envelope.optString("clientMsgId")

        when (type) {
            HEARTBEAT -> Unit
            APP_AUTH_RES -> requestAccounts()
            ACCOUNTS_RES -> handleAccounts(payload)
            ACCOUNT_AUTH_RES -> {
                requestSymbols()
                requestReconcile()
            }
            SYMBOLS_LIST_RES -> handleSymbols(payload)
            SYMBOL_BY_ID_RES -> handleSymbolDetails(payload)
            RECONCILE_RES -> handleReconcile(payload)
            EXECUTION_EVENT -> handleExecution(payload)
            ORDER_ERROR_EVENT -> handleOrderError(payload)
            GET_TRENDBARS_RES -> handleHistory(clientMsgId, payload)
            SUBSCRIBE_SPOTS_RES -> {
                spotReady = true
                maybeSubscribeLive()
            }
            SUBSCRIBE_LIVE_TRENDBAR_RES -> listener.onState("Priore ativo · XAUUSD M5/M15")
            SPOT_EVENT -> handleSpot(payload)
            OA_ERROR_RES, COMMON_ERROR_RES -> handleError(payload)
        }
    }

    private fun requestAccounts() {
        listener.onState("Aplicação autenticada · procurando conta…")
        send(
            ACCOUNTS_REQ,
            JSONObject().put("accessToken", credentials.accessToken),
        )
    }

    private fun handleAccounts(payload: JSONObject) {
        val accounts = payload.optJSONArray("ctidTraderAccount") ?: JSONArray()
        val wantsLive = credentials.environment == "live"
        var chosen: Long? = null
        for (index in 0 until accounts.length()) {
            val account = accounts.optJSONObject(index) ?: continue
            if (account.optBoolean("isLive", false) == wantsLive) {
                chosen = account.flexLong("ctidTraderAccountId")
                if (chosen != null) break
            }
        }

        if (chosen == null) {
            listener.onError(
                "Nenhuma conta ${credentials.environment} foi autorizada por este token.",
            )
            return
        }

        accountId = chosen
        listener.onState("Conta encontrada · autenticando…")
        send(
            ACCOUNT_AUTH_REQ,
            JSONObject()
                .put("ctidTraderAccountId", chosen)
                .put("accessToken", credentials.accessToken),
        )
    }

    private fun requestSymbols() {
        val account = requireAccount()
        listener.onState("Conta autenticada · procurando XAUUSD…")
        send(
            SYMBOLS_LIST_REQ,
            JSONObject()
                .put("ctidTraderAccountId", account)
                .put("includeArchivedSymbols", false),
        )
    }

    private fun requestReconcile() {
        send(
            RECONCILE_REQ,
            JSONObject()
                .put("ctidTraderAccountId", requireAccount())
                .put("returnProtectionOrders", false),
        )
    }

    private fun handleSymbols(payload: JSONObject) {
        val target = normalizeSymbol(symbolName)
        val symbols = payload.optJSONArray("symbol") ?: JSONArray()
        var id: Long? = null
        var resolvedName = ""

        for (index in 0 until symbols.length()) {
            val symbol = symbols.optJSONObject(index) ?: continue
            val name = symbol.optString("symbolName")
            if (normalizeSymbol(name) == target) {
                id = symbol.flexLong("symbolId")
                resolvedName = name
                break
            }
        }

        if (id == null) {
            val candidates = mutableListOf<String>()
            for (index in 0 until symbols.length()) {
                val name = symbols.optJSONObject(index)?.optString("symbolName").orEmpty()
                if (name.contains("XAU", ignoreCase = true)) candidates += name
            }
            listener.onError("XAUUSD não encontrado. Símbolos de ouro: ${candidates.take(8)}")
            return
        }

        symbolId = id
        listener.onState("$resolvedName encontrado · carregando detalhes…")
        send(
            SYMBOL_BY_ID_REQ,
            JSONObject()
                .put("ctidTraderAccountId", requireAccount())
                .put("symbolId", JSONArray().put(id)),
        )
    }

    private fun handleSymbolDetails(payload: JSONObject) {
        val symbols = payload.optJSONArray("symbol") ?: JSONArray()
        val symbol = symbols.optJSONObject(0)
        if (symbol == null) {
            listener.onError("A cTrader não retornou os detalhes do XAUUSD.")
            return
        }

        digits = symbol.optInt("digits", 2)
        minVolume = symbol.flexLong("minVolume")
        stepVolume = symbol.flexLong("stepVolume")
        if (minVolume == null) {
            listener.onError("XAUUSD sem minVolume informado; autoexecução DEMO ficará bloqueada.")
        }
        listener.onState("Carregando histórico M5/M15…")
        requestHistory("M5", 300)
        requestHistory("M15", 300)
        send(
            SUBSCRIBE_SPOTS_REQ,
            JSONObject()
                .put("ctidTraderAccountId", requireAccount())
                .put("symbolId", JSONArray().put(requireSymbol()))
                .put("subscribeToSpotTimestamp", true),
        )
    }

    private fun handleReconcile(payload: JSONObject) {
        val positions = payload.optJSONArray("position") ?: JSONArray()
        for (index in 0 until positions.length()) {
            val position = positions.optJSONObject(index) ?: continue
            val tradeData = position.optJSONObject("tradeData") ?: JSONObject()
            if (tradeData.optString("label") != DEMO_LABEL) continue
            listener.onDemoExecution(
                DemoExecutionEvent(
                    executionType = EXECUTION_RECOVERED,
                    positionId = position.flexLong("positionId"),
                    executionPrice = position.optNullableDouble("price"),
                    positionStatus = position.optInt("positionStatus", 1),
                    volume = tradeData.flexLong("volume"),
                    label = DEMO_LABEL,
                    description = "Posição Priore DEMO recuperada após reconexão.",
                ),
            )
        }
    }

    private fun handleExecution(payload: JSONObject) {
        val position = payload.optJSONObject("position")
        val order = payload.optJSONObject("order")
        val deal = payload.optJSONObject("deal")
        val positionTradeData = position?.optJSONObject("tradeData")
        val orderTradeData = order?.optJSONObject("tradeData")
        val label = deal?.optString("label").orEmpty()
            .ifBlank { positionTradeData?.optString("label").orEmpty() }
            .ifBlank { orderTradeData?.optString("label").orEmpty() }
        if (label != DEMO_LABEL) return

        val closeDetail = deal?.optJSONObject("closePositionDetail")
        val grossProfit = closeDetail?.let { detail ->
            val raw = detail.flexLong("grossProfit") ?: return@let null
            val moneyDigits = detail.optInt("moneyDigits", 2)
            raw / 10.0.pow(moneyDigits)
        }
        listener.onDemoExecution(
            DemoExecutionEvent(
                executionType = payload.optInt("executionType", -1),
                positionId = position?.flexLong("positionId") ?: deal?.flexLong("positionId"),
                orderId = order?.flexLong("orderId") ?: deal?.flexLong("orderId"),
                executionPrice = deal?.optNullableDouble("executionPrice")
                    ?: position?.optNullableDouble("price"),
                positionStatus = position?.optInt("positionStatus", -1)?.takeIf { it >= 0 },
                grossProfit = grossProfit,
                volume = deal?.flexLong("filledVolume") ?: positionTradeData?.flexLong("volume"),
                label = label,
                errorCode = payload.optString("errorCode"),
            ),
        )
    }

    private fun handleOrderError(payload: JSONObject) {
        val code = payload.optString("errorCode", "ORDER_ERROR")
        val description = payload.optString("description")
        listener.onDemoExecution(
            DemoExecutionEvent(
                executionType = EXECUTION_REJECTED,
                positionId = payload.flexLong("positionId"),
                orderId = payload.flexLong("orderId"),
                label = DEMO_LABEL,
                errorCode = code,
                description = description,
            ),
        )
        if (code.contains("PERMISSION", true) || code.contains("AUTH", true) ||
            description.contains("permission", true) || description.contains("scope", true)
        ) {
            listener.onError("Token sem permissão de trading. Reautorize a Priore com scope=trading para operar apenas na DEMO.")
        } else {
            listener.onError("Ordem DEMO rejeitada: $code${if (description.isBlank()) "" else " · $description"}")
        }
    }

    private fun requestHistory(timeframe: String, count: Int) {
        val now = System.currentTimeMillis()
        val minutes = if (timeframe == "M5") 5 else 15
        val from = now - count.toLong() * minutes * 60_000L * 4L
        val id = "history-$timeframe-${UUID.randomUUID()}"
        historyRequest[id] = timeframe

        send(
            GET_TRENDBARS_REQ,
            JSONObject()
                .put("ctidTraderAccountId", requireAccount())
                .put("symbolId", requireSymbol())
                .put("period", periodValue(timeframe))
                .put("fromTimestamp", from)
                .put("toTimestamp", now)
                .put("count", count),
            clientMsgId = id,
        )
    }

    private fun handleHistory(clientMsgId: String, payload: JSONObject) {
        val timeframe = historyRequest.remove(clientMsgId)
            ?: parsePeriod(payload.opt("period"))
            ?: return
        val array = payload.optJSONArray("trendbar") ?: JSONArray()
        val now = Instant.now()
        val candles = buildList {
            for (index in 0 until array.length()) {
                val bar = array.optJSONObject(index) ?: continue
                val candle = decodeTrendbar(bar, timeframe) ?: continue
                val duration = if (timeframe == "M5") 300L else 900L
                if (!now.isBefore(candle.openedAt.plusSeconds(duration))) add(candle)
            }
        }.sortedBy { it.openedAt }

        listener.onHistory(timeframe, candles)
        candles.lastOrNull()?.let {
            lastClosedMinute[timeframe] = it.openedAt.epochSecond / 60L
        }
        historyReady += timeframe
        maybeSubscribeLive()
    }

    private fun maybeSubscribeLive() {
        if (liveSubscribed) return
        if (!spotReady || !historyReady.containsAll(listOf("M5", "M15"))) return

        liveSubscribed = true
        for (timeframe in listOf("M5", "M15")) {
            send(
                SUBSCRIBE_LIVE_TRENDBAR_REQ,
                JSONObject()
                    .put("ctidTraderAccountId", requireAccount())
                    .put("symbolId", requireSymbol())
                    .put("period", periodValue(timeframe)),
            )
        }
    }

    private fun handleSpot(payload: JSONObject) {
        val id = payload.flexLong("symbolId") ?: return
        if (id != requireSymbol()) return

        val bid = payload.flexLong("bid")?.div(100000.0)
        val ask = payload.flexLong("ask")?.div(100000.0)
        val price = when {
            bid != null && ask != null -> (bid + ask) / 2.0
            bid != null -> bid
            ask != null -> ask
            else -> null
        }
        price?.let { listener.onPrice(roundPrice(it)) }

        val bars = payload.optJSONArray("trendbar") ?: return
        val closedBars = buildList {
            for (index in 0 until bars.length()) {
                val bar = bars.optJSONObject(index) ?: continue
                val timeframe = parsePeriod(bar.opt("period")) ?: continue
                if (timeframe != "M5" && timeframe != "M15") continue
                decodeTrendbar(bar, timeframe)?.let(::add)
            }
        }.sortedBy { candle ->
            if (candle.timeframe == "M15") 0 else 1
        }

        for (candle in closedBars) {
            val minute = candle.openedAt.epochSecond / 60L
            val previous = lastClosedMinute[candle.timeframe]
            if (previous != null && minute <= previous) continue
            lastClosedMinute[candle.timeframe] = minute
            listener.onClosedCandle(candle)
        }
    }

    private fun handleError(payload: JSONObject) {
        val code = payload.optString("errorCode", "UNKNOWN")
        val description = payload.optString("description")
        val message = listOf(code, description)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

        if (code == "OA_AUTH_TOKEN_EXPIRED" || code == "CH_ACCESS_TOKEN_INVALID") {
            listener.onAuthExpired(message)
        } else {
            listener.onError("cTrader: $message")
        }
    }

    private fun decodeTrendbar(bar: JSONObject, timeframe: String): Candle? {
        val lowRaw = bar.flexLong("low") ?: return null
        val openDelta = bar.flexLong("deltaOpen") ?: 0L
        val closeDelta = bar.flexLong("deltaClose") ?: 0L
        val highDelta = bar.flexLong("deltaHigh") ?: 0L
        val minute = bar.flexLong("utcTimestampInMinutes") ?: return null

        return Candle(
            timeframe = timeframe,
            openedAt = Instant.ofEpochSecond(minute * 60L),
            open = roundPrice((lowRaw + openDelta) / 100000.0),
            high = roundPrice((lowRaw + highDelta) / 100000.0),
            low = roundPrice(lowRaw / 100000.0),
            close = roundPrice((lowRaw + closeDelta) / 100000.0),
            volume = bar.flexLong("volume") ?: 0L,
        )
    }

    private fun send(
        type: Int,
        payload: JSONObject,
        clientMsgId: String = UUID.randomUUID().toString(),
    ) {
        val envelope = JSONObject()
            .put("clientMsgId", clientMsgId)
            .put("payloadType", type)
            .put("payload", payload)

        if (socket?.send(envelope.toString()) != true && type != HEARTBEAT) {
            listener.onError("Não foi possível enviar mensagem à cTrader.")
        }
    }

    private fun resetSessionState() {
        accountId = null
        symbolId = null
        digits = 2
        minVolume = null
        stepVolume = null
        spotReady = false
        liveSubscribed = false
        historyReady.clear()
        historyRequest.clear()
        lastClosedMinute.clear()
    }

    private fun requireAccount(): Long = accountId ?: error("Conta ainda não autenticada")
    private fun requireSymbol(): Long = symbolId ?: error("Símbolo ainda não resolvido")

    private fun normalizeSymbol(value: String): String =
        value.uppercase().filter { it.isLetterOrDigit() }

    private fun roundPrice(value: Double): Double =
        BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).toDouble()

    private fun periodValue(timeframe: String): Int = when (timeframe.uppercase()) {
        "M5" -> 5
        "M15" -> 7
        else -> error("Timeframe não suportado: $timeframe")
    }

    private fun parsePeriod(value: Any?): String? = when (value) {
        is String -> when (value.uppercase()) {
            "M5", "5" -> "M5"
            "M15", "7" -> "M15"
            else -> null
        }
        is Number -> when (value.toInt()) {
            5 -> "M5"
            7 -> "M15"
            else -> null
        }
        else -> null
    }

    private fun JSONObject.flexLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    companion object {
        private const val HEARTBEAT = 51
        private const val COMMON_ERROR_RES = 50
        private const val APP_AUTH_REQ = 2100
        private const val APP_AUTH_RES = 2101
        private const val ACCOUNT_AUTH_REQ = 2102
        private const val ACCOUNT_AUTH_RES = 2103
        private const val NEW_ORDER_REQ = 2106
        private const val SYMBOLS_LIST_REQ = 2114
        private const val SYMBOLS_LIST_RES = 2115
        private const val SYMBOL_BY_ID_REQ = 2116
        private const val SYMBOL_BY_ID_RES = 2117
        private const val RECONCILE_REQ = 2124
        private const val RECONCILE_RES = 2125
        private const val EXECUTION_EVENT = 2126
        private const val SUBSCRIBE_SPOTS_REQ = 2127
        private const val SUBSCRIBE_SPOTS_RES = 2128
        private const val SPOT_EVENT = 2131
        private const val ORDER_ERROR_EVENT = 2132
        private const val SUBSCRIBE_LIVE_TRENDBAR_REQ = 2135
        private const val GET_TRENDBARS_REQ = 2137
        private const val GET_TRENDBARS_RES = 2138
        private const val OA_ERROR_RES = 2142
        private const val ACCOUNTS_REQ = 2149
        private const val ACCOUNTS_RES = 2150
        private const val SUBSCRIBE_LIVE_TRENDBAR_RES = 2165

        private const val ORDER_TYPE_MARKET = 1
        private const val TRADE_SIDE_BUY = 1
        private const val TRADE_SIDE_SELL = 2
        private const val EXECUTION_REJECTED = 7
        private const val EXECUTION_RECOVERED = 100
        private const val DEMO_LABEL = "PRIORE_DEMO"
    }
}
