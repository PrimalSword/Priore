package com.primalsword.priore

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

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

    override fun onCreate() {
        super.onCreate()
        PrioreNotifications.createChannels(this)
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
                if (plan.kind != SignalKind.WAIT && plan.kind != lastAlertKind) {
                    PrioreNotifications.signal(this, plan)
                }
                lastAlertKind = plan.kind
                updateState("M5 ${fmt(candle.close)} · ${plan.kind.name.replace('_', ' ')}")
            }
        } else {
            updateState("M15 atualizado · ${fmt(candle.close)}")
        }
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
