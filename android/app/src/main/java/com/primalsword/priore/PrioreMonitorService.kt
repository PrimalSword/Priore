package com.primalsword.priore

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import java.time.Instant
import java.util.UUID

class PrioreMonitorService : Service(), CTraderWebSocketClient.Listener {
    private val engine = StrategyEngine()
    private var client: CTraderWebSocketClient? = null
    private var credentials: CTraderCredentials? = null
    private var refreshingToken = false
    private var currentStatus = "Iniciando…"
    private var latestPrice: Double? = null
    private var lastM5Close: Double? = null
    private var lastPricePersistMs = 0L
    private var lastAlertKind: SignalKind = SignalKind.WAIT
    private var activeSetup: ActiveSetup? = null

    override fun onCreate() {
        super.onCreate()
        PrioreNotifications.createChannels(this)
        activeSetup = TradeLifecycleStore.loadActive(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        startAsForeground("Iniciando monitoramento local…")
        if (client == null) startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        client?.close()
        client = null
        super.onDestroy()
    }

    private fun startMonitoring() {
        val saved = CredentialStore.load(this)
        if (saved == null) {
            updateState("Credenciais cTrader não configuradas.", running = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        credentials = saved
        updateState("Conectando à cTrader…", running = true)
        client = CTraderWebSocketClient(
            credentials = saved,
            listener = this,
        ).also { it.connect() }
    }

    private fun stopMonitoring() {
        client?.close()
        client = null
        lastAlertKind = SignalKind.WAIT
        updateState("Monitoramento parado.", running = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground(status: String) {
        currentStatus = status
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            PrioreNotifications.FOREGROUND_ID,
            PrioreNotifications.foreground(this, status),
            type,
        )
    }

    private fun updateForeground(status: String) {
        currentStatus = status
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(
            PrioreNotifications.FOREGROUND_ID,
            PrioreNotifications.foreground(this, status),
        )
    }

    private fun updateState(status: String, running: Boolean = true) {
        currentStatus = status
        SignalStore.saveMonitor(
            this,
            running = running,
            status = status,
            latestPrice = latestPrice,
            lastM5Close = lastM5Close,
        )
        if (running) updateForeground(status)
        broadcastState()
    }

    private fun broadcastState() {
        sendBroadcast(
            Intent(ACTION_STATE_CHANGED)
                .setPackage(packageName),
        )
    }

    override fun onState(state: String) {
        updateState(state)
    }

    override fun onPrice(price: Double) {
        latestPrice = price
        val setup = activeSetup
        if (setup != null && !setup.isTerminal) {
            activeSetup = setup.copy(currentPrice = price, updatedAt = Instant.now())
            TradeLifecycleStore.saveActive(this, activeSetup)
        }

        val now = System.currentTimeMillis()
        if (now - lastPricePersistMs >= 3000L) {
            lastPricePersistMs = now
            SignalStore.saveMonitor(
                this,
                running = true,
                status = currentStatus,
                latestPrice = latestPrice,
                lastM5Close = lastM5Close,
            )
            broadcastState()
        }
    }

    override fun onHistory(timeframe: String, candles: List<Candle>) {
        engine.seed(timeframe, candles)
        if (timeframe == "M5") lastM5Close = candles.lastOrNull()?.close
        updateState("Histórico $timeframe carregado (${candles.size} velas)")
    }

    override fun onClosedCandle(candle: Candle) {
        val plan = engine.onClosedCandle(candle)
        if (candle.timeframe == "M5") {
            lastM5Close = candle.close
            if (plan != null) {
                SignalStore.savePlan(this, plan)

                val runningSetup = activeSetup?.takeIf { !it.isTerminal }
                if (runningSetup == null) {
                    if (plan.kind != SignalKind.WAIT && plan.kind != lastAlertKind) {
                        PrioreNotifications.signal(this, plan)
                    }
                    lastAlertKind = plan.kind
                    maybeStartDemoSetup(plan)
                    updateState("M5 ${fmt(candle.close)} · ${plan.kind.name.replace('_', ' ')}")
                } else {
                    updateState(
                        "M5 ${fmt(candle.close)} · setup ${runningSetup.signalKind.name.replace("_SETUP", "")} ${runningSetup.status.name}",
                    )
                }
            }
        } else {
            updateState("M15 atualizado · ${fmt(candle.close)}")
        }
    }

    private fun maybeStartDemoSetup(plan: TradePlan) {
        if (plan.kind != SignalKind.BUY_SETUP && plan.kind != SignalKind.SELL_SETUP) return
        if (!TradeLifecycleStore.isAutoDemoEnabled(this)) return

        val saved = credentials ?: return
        if (saved.environment != "demo") {
            updateState("Autoexecução bloqueada: o Priore só envia ordens em conta DEMO.")
            return
        }

        val entry = plan.entry ?: return
        val stop = plan.stop ?: return
        val target = plan.target ?: return
        val setup = ActiveSetup(
            setupId = "priore-${UUID.randomUUID()}",
            signalKind = plan.kind,
            status = SetupStatus.PENDING_EXECUTION,
            signalEntry = entry,
            stop = stop,
            target = target,
            riskReward = plan.riskReward ?: 1.8,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            currentPrice = latestPrice,
            note = "Aguardando execução da ordem de mercado na conta DEMO.",
        )
        activeSetup = setup
        TradeLifecycleStore.saveActive(this, setup)
        broadcastState()
        client?.placeDemoMarketOrder(plan, setup.setupId)
    }

    override fun onDemoExecution(event: DemoExecutionEvent) {
        val setup = activeSetup ?: TradeLifecycleStore.loadActive(this)
        if (setup == null) {
            if (event.executionType == 100) {
                updateState("Posição Priore DEMO encontrada, mas sem lifecycle local correspondente.")
            }
            return
        }

        if (event.executionType == 7 || event.errorCode.isNotBlank()) {
            val failed = setup.copy(
                status = SetupStatus.ERROR,
                updatedAt = Instant.now(),
                positionId = event.positionId ?: setup.positionId,
                orderId = event.orderId ?: setup.orderId,
                note = listOf(event.errorCode, event.description)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "Ordem DEMO rejeitada pela cTrader." },
            )
            finishSetup(failed, "Ordem DEMO rejeitada")
            return
        }

        val isClosed = event.positionStatus == 2 || event.grossProfit != null
        if (isClosed) {
            val gross = event.grossProfit
            val outcome = when {
                gross != null && gross > 0.0 -> SetupStatus.WIN
                gross != null && gross < 0.0 -> SetupStatus.LOSS
                event.executionPrice != null && setup.signalKind == SignalKind.BUY_SETUP &&
                    event.executionPrice >= setup.target -> SetupStatus.WIN
                event.executionPrice != null && setup.signalKind == SignalKind.SELL_SETUP &&
                    event.executionPrice <= setup.target -> SetupStatus.WIN
                else -> SetupStatus.LOSS
            }
            val closed = setup.copy(
                status = outcome,
                updatedAt = Instant.now(),
                positionId = event.positionId ?: setup.positionId,
                orderId = event.orderId ?: setup.orderId,
                closePrice = event.executionPrice,
                grossProfit = gross,
                currentPrice = event.executionPrice ?: latestPrice,
                note = if (outcome == SetupStatus.WIN) "Posição DEMO encerrada com resultado positivo." else "Posição DEMO encerrada com resultado negativo.",
            )
            finishSetup(closed, if (outcome == SetupStatus.WIN) "DEMO WIN" else "DEMO LOSS")
            return
        }

        val opened = setup.copy(
            status = SetupStatus.OPEN,
            updatedAt = Instant.now(),
            actualEntry = event.executionPrice ?: setup.actualEntry,
            currentPrice = latestPrice,
            positionId = event.positionId ?: setup.positionId,
            orderId = event.orderId ?: setup.orderId,
            volume = event.volume ?: setup.volume,
            note = if (event.executionType == 100) {
                "Posição DEMO recuperada após reconexão."
            } else {
                "Posição DEMO aberta e protegida por SL/TP enviados à cTrader."
            },
        )
        activeSetup = opened
        TradeLifecycleStore.saveActive(this, opened)
        updateState("DEMO ${opened.signalKind.name.replace("_SETUP", "")} aberta · posição ${opened.positionId ?: "..."}")
    }

    private fun finishSetup(setup: ActiveSetup, headline: String) {
        activeSetup = setup
        TradeLifecycleStore.saveActive(this, setup)
        TradeLifecycleStore.appendHistory(this, setup)
        PrioreNotifications.demoResult(this, setup)
        updateState("$headline · ${setup.signalKind.name.replace("_SETUP", "")}")
    }

    override fun onAuthExpired(reason: String) {
        if (refreshingToken) return
        val current = credentials ?: return
        refreshingToken = true
        updateState("Token expirado · renovando automaticamente…")
        TokenRefresher.refresh(current) { result ->
            refreshingToken = false
            result.onSuccess { renewed ->
                credentials = renewed
                CredentialStore.save(this, renewed)
                updateState("Token renovado · reconectando…")
                client?.replaceCredentials(renewed)
            }.onFailure { error ->
                updateState("Falha ao renovar token: ${error.message ?: reason}")
            }
        }
    }

    override fun onError(message: String) {
        updateState(message)
    }

    private fun fmt(value: Double): String = "%.2f".format(java.util.Locale.US, value)

    companion object {
        const val ACTION_START = "com.primalsword.priore.action.START"
        const val ACTION_STOP = "com.primalsword.priore.action.STOP"
        const val ACTION_STATE_CHANGED = "com.primalsword.priore.action.STATE_CHANGED"
    }
}
