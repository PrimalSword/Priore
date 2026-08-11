package com.primalsword.priore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object PrioreNotifications {
    const val MONITOR_CHANNEL = "priore_monitor"
    const val SIGNAL_CHANNEL = "priore_signals"
    const val FOREGROUND_ID = 1709
    private const val SIGNAL_ID_BASE = 2000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                MONITOR_CHANNEL,
                "Monitoramento do ouro",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Mantém o monitoramento local do XAUUSD ativo"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SIGNAL_CHANNEL,
                "Leituras do ouro",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Pré-alertas e setups BUY/SELL gerados pelo Priore"
                enableVibration(true)
            },
        )
    }

    fun foreground(context: Context, status: String): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, PrioreMonitorService::class.java).setAction(PrioreMonitorService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, MONITOR_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Priore · XAUUSD")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setContentIntent(openIntent)
            .addAction(0, "Parar", stopIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun signal(context: Context, plan: TradePlan) {
        if (plan.kind == SignalKind.WAIT) return
        val title = when (plan.kind) {
            SignalKind.WATCH_BUY -> "Priore · atenção para compra em XAUUSD"
            SignalKind.WATCH_SELL -> "Priore · atenção para venda em XAUUSD"
            SignalKind.BUY_SETUP -> "Priore · possível compra em XAUUSD"
            SignalKind.SELL_SETUP -> "Priore · possível venda em XAUUSD"
            SignalKind.WAIT -> return
        }
        val body = buildString {
            append(plan.reason)
            if (plan.nextTrigger.isNotBlank()) append("\nPara confirmar: ").append(plan.nextTrigger)
            plan.entry?.let { append("\nEntrada: ").append(fmt(it)) }
            plan.stop?.let { append(" · SL: ").append(fmt(it)) }
            plan.target?.let { append(" · TP: ").append(fmt(it)) }
            append("\nM15: ").append(trendLabel(plan.trendM15))
        }
        val openIntent = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SIGNAL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(
                SIGNAL_ID_BASE + (System.currentTimeMillis() % 1000).toInt(),
                notification,
            )
        } catch (_: SecurityException) {
            // Android 13+: o usuário ainda não concedeu a permissão de notificações.
        }
    }

    private fun trendLabel(value: String): String = when (value) {
        "bullish" -> "altista"
        "bearish" -> "baixista"
        else -> "neutro"
    }

    private fun fmt(value: Double): String = "%.2f".format(java.util.Locale.US, value)
}
