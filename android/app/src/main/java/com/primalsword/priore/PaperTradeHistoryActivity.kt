package com.primalsword.priore

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.graphics.drawable.toDrawable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PaperTradeHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val active = PaperTradeStore.loadActive(this)
        val history = PaperTradeStore.history(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 17, 20)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(40))
        }
        scroll.addView(root)

        root.addView(text("SIMULAÇÕES", 28, true))
        root.addView(text("Paper trading local do Priore · nenhuma ordem é enviada", 14, false, Color.LTGRAY).withTop(4))

        val back = Button(this).apply {
            text = "Voltar ao mercado"
            setOnClickListener { finish() }
        }
        root.addView(back.withTop(18))

        if (active?.isOpen == true) {
            root.addView(tradeCard("EM ANDAMENTO", active).withTop(18))
        }

        val wins = history.count { it.status == PaperTradeStatus.WIN }
        val losses = history.count { it.status == PaperTradeStatus.LOSS }
        val resolved = wins + losses
        val totalPoints = history.mapNotNull { it.resultPoints() }.sum()
        val summary = buildString {
            append("Finalizadas: ").append(history.size)
            append("\nWIN: ").append(wins).append(" · LOSS: ").append(losses)
            if (resolved > 0) append("\nTaxa de acerto: ").append("%.1f%%".format(100.0 * wins / resolved))
            append("\nSaldo teórico: ").append(if (totalPoints >= 0) "+" else "").append(fmt(totalPoints)).append(" pontos")
        }
        root.addView(card("RESUMO", summary).withTop(18))

        if (history.isEmpty()) {
            root.addView(card("HISTÓRICO", "Nenhuma simulação finalizada ainda.").withTop(18))
        } else {
            history.forEachIndexed { index, trade ->
                root.addView(tradeCard("#${history.size - index} · ${trade.status.name}", trade).withTop(12))
            }
        }

        setContentView(scroll)
    }

    private fun tradeCard(title: String, trade: PaperTrade): LinearLayout {
        val current = trade.currentPrice
        val body = buildString {
            append(if (trade.signalKind == SignalKind.BUY_SETUP) "BUY" else "SELL")
            append(" · ").append(formatTimestamp(trade.createdAt))
            append("\nEntrada: ").append(fmt(trade.entry))
            append(" · SL: ").append(fmt(trade.stop))
            append(" · TP: ").append(fmt(trade.target))
            append("\nR:R inicial: ").append("%.2f".format(trade.riskReward))
            current?.let {
                append("\nAtual: ").append(fmt(it))
                val points = trade.pointsAt(it)
                append(" · ").append(if (points >= 0) "+" else "").append(fmt(points)).append(" pts")
            }
            trade.closePrice?.let {
                append("\nSaída: ").append(fmt(it))
                trade.resultPoints()?.let { points ->
                    append(" · resultado: ").append(if (points >= 0) "+" else "").append(fmt(points)).append(" pts")
                }
            }
            append("\nM15: ").append(trendLabel(trade.trendM15))
            trade.support?.let { append(" · suporte ").append(fmt(it)) }
            trade.resistance?.let { append(" · resistência ").append(fmt(it)) }
            trade.atrM5?.let { append("\nATR M5: ").append(fmt(it)) }
            if (trade.reason.isNotBlank()) append("\n\n").append(trade.reason)
            append("\nDuração: ").append(durationLabel(trade))
        }
        return card(title, body)
    }

    private fun durationLabel(trade: PaperTrade): String {
        val end = trade.closedAt ?: Instant.now()
        val seconds = Duration.between(trade.createdAt, end).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun formatTimestamp(instant: Instant): String =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(instant)

    private fun trendLabel(value: String): String = when (value) {
        "bullish" -> "altista"
        "bearish" -> "baixista"
        else -> "neutro"
    }

    private fun card(title: String, body: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = Color.rgb(28, 31, 36).toDrawable()
        addView(text(title, 17, true))
        addView(text(body, 14, false, Color.LTGRAY).withTop(8))
    }

    private fun text(value: String, sizeSp: Int, bold: Boolean, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp.toFloat()
        setTextColor(color)
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun <T : View> T.withTop(value: Int): T {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(value)
        }
        return this
    }

    private fun fmt(value: Double): String = "%.2f".format(java.util.Locale.US, value)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
