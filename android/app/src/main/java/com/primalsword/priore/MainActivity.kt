package com.primalsword.priore

import android.Manifest
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.drawable.toDrawable

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(15, 17, 20))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(40))
        }
        scroll.addView(root)

        root.addView(text("PRIORE", 30, true))
        root.addView(text("Assistente de mercado · XAUUSD", 15, false, Color.LTGRAY).withTop(4))

        val configured = PrioreApp.firebaseConfigReady()
        root.addView(
            card(
                "Monitoramento",
                if (configured) "Notificações conectadas · M5 + M15" else "Firebase ainda não configurado neste aparelho",
            ).withTop(24),
        )

        val signal = SignalStore.load(this)
        if (signal == null) {
            root.addView(card("Último sinal", "Nenhum sinal recebido ainda.").withTop(14))
        } else {
            val details = buildString {
                append(signal.reason)
                if (signal.trendM15.isNotBlank()) append("\n\nM15: ").append(signal.trendM15)
                if (signal.entry.isNotBlank()) append("\nEntrada: ").append(signal.entry)
                if (signal.stop.isNotBlank()) append("\nStop técnico: ").append(signal.stop)
                if (signal.target.isNotBlank()) append("\nAlvo técnico: ").append(signal.target)
                if (signal.support.isNotBlank()) append("\nSuporte: ").append(signal.support)
                if (signal.resistance.isNotBlank()) append("\nResistência: ").append(signal.resistance)
                if (signal.atrM5.isNotBlank()) append("\nATR M5: ").append(signal.atrM5)
                if (signal.timestamp.isNotBlank()) append("\n\n").append(signal.timestamp)
            }
            root.addView(card(signal.kind.replace('_', ' '), details).withTop(14))
        }

        root.addView(
            text(
                "O Priore analisa velas fechadas no servidor e envia o alerta para este aparelho. Nesta versão ele não executa ordens.",
                13,
                false,
                Color.GRAY,
            ).withTop(22),
        )

        setContentView(scroll)
    }

    private fun card(title: String, body: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = Color.rgb(28, 31, 36).toDrawable().apply { alpha = 255 }
        addView(text(title, 18, true))
        addView(text(body, 14, false, Color.LTGRAY).withTop(8))
    }

    private fun text(
        value: String,
        sizeSp: Int,
        bold: Boolean,
        color: Int = Color.WHITE,
    ): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun <T : android.view.View> T.withTop(dp: Int): T {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = this@MainActivity.dp(dp) }
        return this
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
