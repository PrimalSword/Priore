package com.primalsword.priore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PrioreMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty()) return
        SignalStore.save(this, message.data)
        showNotification(message.data)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (PrioreApp.firebaseConfigReady()) {
            FirebaseMessaging.getInstance().subscribeToTopic(BuildConfig.FIREBASE_TOPIC)
        }
    }

    private fun showNotification(data: Map<String, String>) {
        createChannel()

        val kind = data["kind"].orEmpty()
        val entry = data["entry"].orEmpty()
        val target = data["target"].orEmpty()
        val title = when (kind) {
            "BUY_SETUP" -> "Priore · possível compra em XAUUSD"
            "SELL_SETUP" -> "Priore · possível venda em XAUUSD"
            else -> "Priore · atualização do ouro"
        }
        val body = buildString {
            append(data["reason"].orEmpty())
            if (entry.isNotBlank()) append(" · Entrada ").append(entry)
            if (target.isNotBlank()) append(" · Alvo ").append(target)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ notification permission was not granted yet.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sinais do ouro",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Alertas técnicos do Priore para XAUUSD"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "priore_xau_signals"
        private const val NOTIFICATION_ID = 1709
    }
}
