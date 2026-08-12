package com.primalsword.priore

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private var receiverRegistered = false
    private val clockHandler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var currentScrollView: ScrollView? = null
    private var preservedScrollY: Int = 0

    private val clockTick = object : Runnable {
        override fun run() {
            countdownView?.text = candleCountdownText()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { render(preserveScroll = true) }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            preservedScrollY = currentScrollView?.scrollY ?: preservedScrollY
            render(preserveScroll = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                stateReceiver,
                IntentFilter(PrioreMonitorService.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
        render(preserveScroll = true)
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    override fun onStop() {
        preservedScrollY = currentScrollView?.scrollY ?: preservedScrollY
        clockHandler.removeCallbacks(clockTick)
        if (receiverRegistered) {
            unregisterReceiver(stateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    private fun render(preserveScroll: Boolean = false) {
        val restoreY = if (preserveScroll) currentScrollView?.scrollY ?: preservedScrollY else 0
        val monitor = SignalStore.loadMonitor(this)
        val credentials = CredentialStore.load(this)
        val signal = SignalStore.loadSignal(this)
        val paperTrade = PaperTradeStore.loadActive(this)
        val paperHistory = PaperTradeStore.history(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 17, 20)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(40))
        }
        scroll.addView(root)

        root.addView(text("PRIORE", 30, true))
        root.addView(
            text(
                "Assistente local de mercado · XAUUSD",
                15,
                false,
                Color.LTGRAY,
            ).withTop(4),
        )

        val stateBody = buildString {
            append(if (monitor.running) "ATIVO" else "PARADO")
            append("\n").append(monitor.status)
            if (monitor.latestPrice.isNotBlank()) append("\n\nXAUUSD: ").append(monitor.latestPrice)
            if (monitor.lastM5Close.isNotBlank()) append("\nÚltimo M5: ").append(monitor.lastM5Close)
            if (credentials != null) append("\nAmbiente de dados: ").append(credentials.environment.uppercase())
        }
        root.addView(card("Monitoramento", stateBody).withTop(24))

        countdownView = text(candleCountdownText(), 14, true, Color.LTGRAY)
        root.addView(countdownView!!.withTop(8))

        val simulations = Button(this).apply {
            text = if (paperHistory.isEmpty() && paperTrade?.isOpen != true) {
                "Simulações"
            } else {
                "Simulações e histórico (${paperHistory.size})"
            }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PaperTradeHistoryActivity::class.java))
            }
        }
        root.addView(simulations.withTop(14))

        val configure = Button(this).apply {
            text = if (credentials == null) "Configurar cTrader" else "Atualizar credenciais cTrader"
            setOnClickListener { showCredentialsDialog(credentials) }
        }
        root.addView(configure.withTop(8))

        val start = Button(this).apply {
            text = "Iniciar monitoramento"
            isEnabled = credentials != null && !monitor.running
            setOnClickListener {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, PrioreMonitorService::class.java)
                        .setAction(PrioreMonitorService.ACTION_START),
                )
                Toast.makeText(this@MainActivity, "Priore iniciando…", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(start.withTop(10))

        val stop = Button(this).apply {
            text = "Parar monitoramento"
            isEnabled = monitor.running
            setOnClickListener {
                startService(
                    Intent(this@MainActivity, PrioreMonitorService::class.java)
                        .setAction(PrioreMonitorService.ACTION_STOP),
                )
            }
        }
        root.addView(stop.withTop(8))

        if (paperTrade?.isOpen == true) {
            root.addView(paperTradeCard(paperTrade).withTop(18))
        }

        if (signal == null) {
            root.addView(card("Leitura técnica", "Nenhum fechamento M5 analisado ainda.").withTop(18))
        } else {
            root.addView(signalCard(signal, monitor.latestPrice).withTop(18))
        }

        val update = Button(this).apply {
            text = "Verificar atualização"
            setOnClickListener { checkForUpdates() }
        }
        root.addView(update.withTop(18))

        root.addView(
            text(
                "Versão ${BuildConfig.VERSION_NAME} · atualização manual pelo GitHub",
                12,
                false,
                Color.GRAY,
            ).withTop(8),
        )

        root.addView(
            text(
                "O Priore apenas lê a cTrader. BUY/SELL confirmados viram simulações locais: nenhuma ordem é criada, modificada ou encerrada na corretora.",
                13,
                false,
                Color.GRAY,
            ).withTop(18),
        )

        currentScrollView = scroll
        setContentView(scroll)
        if (restoreY > 0) {
            scroll.post {
                scroll.scrollTo(0, restoreY.coerceAtMost(maxOf(0, scroll.getChildAt(0)?.height?.minus(scroll.height) ?: 0)))
            }
        }
    }

    private fun paperTradeCard(trade: PaperTrade): LinearLayout {
        val current = trade.currentPrice ?: trade.entry
        val points = trade.pointsAt(current)
        val body = buildString {
            append(if (trade.signalKind == SignalKind.BUY_SETUP) "COMPRA SIMULADA" else "VENDA SIMULADA")
            append("\nEntrada: ").append(fmt(trade.entry))
            append("\nAtual: ").append(fmt(current))
            append(" · ").append(if (points >= 0) "+" else "").append(fmt(points)).append(" pts")
            append("\nSL: ").append(fmt(trade.stop)).append(" · TP: ").append(fmt(trade.target))
            append("\nR:R inicial: ").append("%.2f".format(trade.riskReward))
            append("\nDistância SL: ").append(fmt(abs(current - trade.stop)))
            append(" · TP: ").append(fmt(abs(current - trade.target)))
            append("\nGerada há: ").append(ageLabel(trade.createdAt))
            append("\n\nO Priore acompanha esta simulação até o preço tocar o alvo ou o stop.")
        }
        return card("SIMULAÇÃO EM ANDAMENTO", body)
    }

    private fun signalCard(signal: SignalSnapshot, latestRaw: String): LinearLayout {
        val latest = latestRaw.toDoubleOrNull()
        val support = signal.support.toDoubleOrNull()
        val resistance = signal.resistance.toDoubleOrNull()
        val atr = signal.atrM5.toDoubleOrNull()
        val confirmation = signal.confirmationPrice.toDoubleOrNull()
        val invalidation = signal.invalidationPrice.toDoubleOrNull()

        val details = buildString {
            append(signal.reason)
            if (signal.kind == "BUY_SETUP" || signal.kind == "SELL_SETUP") {
                if (signal.nextTrigger.isNotBlank()) append("\n\nSTATUS DO SETUP\n").append(signal.nextTrigger)
                append("\n\nSIMULAÇÃO\nO setup confirmado é registrado automaticamente na aba Simulações, sem enviar ordem à cTrader.")
            } else if (signal.nextTrigger.isNotBlank()) {
                append("\n\nPARA CONFIRMAR\n").append(signal.nextTrigger)
            }
            if (signal.invalidation.isNotBlank()) append("\n\nINVALIDAÇÃO\n").append(signal.invalidation)

            if (latest != null && confirmation != null && signal.kind.startsWith("WATCH")) {
                append("\n\nFalta ").append(fmt(abs(confirmation - latest))).append(" para confirmar")
            }
            if (latest != null && invalidation != null && signal.kind.startsWith("WATCH")) {
                append("\nFalta ").append(fmt(abs(invalidation - latest))).append(" para invalidar")
            }

            if (signal.trendM15.isNotBlank()) append("\n\nM15: ").append(trendLabel(signal.trendM15))
            if (signal.entry.isNotBlank()) append("\nEntrada: ").append(signal.entry)
            if (signal.stop.isNotBlank()) append("\nStop técnico: ").append(signal.stop)
            if (signal.target.isNotBlank()) append("\nAlvo técnico: ").append(signal.target)
            if (signal.entry.isNotBlank() && signal.stop.isNotBlank() && signal.target.isNotBlank()) {
                val e = signal.entry.toDoubleOrNull()
                val s = signal.stop.toDoubleOrNull()
                val t = signal.target.toDoubleOrNull()
                if (e != null && s != null && t != null && abs(e - s) > 0.0) {
                    append("\nR:R: ").append("%.2f".format(abs(t - e) / abs(e - s)))
                }
            }
            if (support != null) {
                append("\nSuporte: ").append(fmt(support))
                if (latest != null && atr != null && atr > 0) {
                    val d = abs(latest - support)
                    append(" · ").append(fmt(d)).append(" · ").append("%.2f ATR".format(d / atr))
                }
            }
            if (resistance != null) {
                append("\nResistência: ").append(fmt(resistance))
                if (latest != null && atr != null && atr > 0) {
                    val d = abs(resistance - latest)
                    append(" · ").append(fmt(d)).append(" · ").append("%.2f ATR".format(d / atr))
                }
            }
            if (signal.atrM5.isNotBlank()) append("\nATR M5: ").append(signal.atrM5)
            if (signal.timestamp.isNotBlank()) {
                append("\n\nAnalisado: ").append(formatTimestamp(signal.timestamp))
                runCatching { Instant.parse(signal.timestamp) }.getOrNull()?.let {
                    append(" · há ").append(ageLabel(it))
                }
            }
        }
        return card(signalTitle(signal.kind), details)
    }

    private fun candleCountdownText(): String {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val m5 = remainingToBoundary(nowSeconds, 5 * 60L)
        val m15 = remainingToBoundary(nowSeconds, 15 * 60L)
        val nextM5 = Instant.ofEpochSecond(nowSeconds + m5)
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        return "M5 fecha em ${mmss(m5)} · próximo ${formatter.format(nextM5)}    |    M15 ${mmss(m15)}"
    }

    private fun remainingToBoundary(nowSeconds: Long, interval: Long): Long {
        val remainder = nowSeconds % interval
        return if (remainder == 0L) interval else interval - remainder
    }

    private fun mmss(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    private fun checkForUpdates() {
        Toast.makeText(this, "Consultando o GitHub…", Toast.LENGTH_SHORT).show()
        UpdateManager.check(
            context = this,
            onResult = { update ->
                if (update == null) {
                    Toast.makeText(this, "Priore já está na versão mais recente.", Toast.LENGTH_SHORT).show()
                } else {
                    showUpdateDialog(update)
                }
            },
            onError = { error -> Toast.makeText(this, "Atualização: $error", Toast.LENGTH_LONG).show() },
        )
    }

    private fun showUpdateDialog(update: UpdateManager.RemoteUpdate) {
        val body = buildString {
            append("Nova versão: ").append(update.versionName)
            if (update.notes.isNotBlank()) append("\n\n").append(update.notes)
            append("\n\nO APK será baixado diretamente do repositório Priore no GitHub.")
        }
        AlertDialog.Builder(this)
            .setTitle("Atualização disponível")
            .setMessage(body)
            .setPositiveButton("Baixar e instalar") { _, _ ->
                UpdateManager.downloadAndInstall(
                    context = this,
                    update = update,
                    onStatus = { status -> Toast.makeText(this, status, Toast.LENGTH_LONG).show() },
                    onError = { error -> Toast.makeText(this, "Atualização: $error", Toast.LENGTH_LONG).show() },
                )
            }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun showCredentialsDialog(existing: CTraderCredentials?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val clientId = secretField("Client ID", false, existing?.clientId.orEmpty())
        val clientSecret = secretField(if (existing == null) "Client Secret" else "Client Secret · vazio mantém o atual", true)
        val accessToken = secretField(if (existing == null) "Access Token" else "Access Token · vazio mantém o atual", true)
        val refreshToken = secretField(if (existing == null) "Refresh Token" else "Refresh Token · vazio mantém o atual", true)
        val environment = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("demo", "live"))
            setSelection(if (existing?.environment == "live") 1 else 0)
        }

        listOf(clientId, clientSecret, accessToken, refreshToken).forEach { field -> container.addView(field.withTop(8)) }
        container.addView(text("Ambiente de dados", 13, true, Color.DKGRAY).withTop(12))
        container.addView(environment)

        val dialog = AlertDialog.Builder(this)
            .setTitle("cTrader Open API")
            .setView(container)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .apply { if (existing != null) setNeutralButton("Apagar", null) }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val updated = CTraderCredentials(
                    clientId = clientId.text.toString().trim().ifBlank { existing?.clientId.orEmpty() },
                    clientSecret = clientSecret.text.toString().trim().ifBlank { existing?.clientSecret.orEmpty() },
                    accessToken = accessToken.text.toString().trim().ifBlank { existing?.accessToken.orEmpty() },
                    refreshToken = refreshToken.text.toString().trim().ifBlank { existing?.refreshToken.orEmpty() },
                    environment = environment.selectedItem.toString(),
                )
                if (updated.clientId.isBlank() || updated.clientSecret.isBlank() || updated.accessToken.isBlank()) {
                    Toast.makeText(this, "Client ID, Secret e Access Token são obrigatórios.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                CredentialStore.save(this, updated)
                Toast.makeText(this, "Credenciais salvas no aparelho.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                render(preserveScroll = true)
            }
            if (existing != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    if (SignalStore.loadMonitor(this).running) {
                        Toast.makeText(this, "Pare o monitoramento antes de apagar as credenciais.", Toast.LENGTH_LONG).show()
                    } else {
                        CredentialStore.clear(this)
                        dialog.dismiss()
                        render(preserveScroll = true)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun signalTitle(kind: String): String = when (kind) {
        "WATCH_BUY" -> "OBSERVAR COMPRA"
        "WATCH_SELL" -> "OBSERVAR VENDA"
        "BUY_SETUP" -> "POSSÍVEL COMPRA"
        "SELL_SETUP" -> "POSSÍVEL VENDA"
        else -> "AGUARDAR"
    }

    private fun trendLabel(value: String): String = when (value) {
        "bullish" -> "altista"
        "bearish" -> "baixista"
        else -> "neutro"
    }

    private fun formatTimestamp(raw: String): String = runCatching {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
        formatter.format(Instant.parse(raw))
    }.getOrDefault(raw)

    private fun ageLabel(instant: Instant): String {
        val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private fun secretField(hint: String, password: Boolean, initial: String = ""): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(initial)
            setSingleLine(true)
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        }

    private fun card(title: String, body: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = Color.rgb(28, 31, 36).toDrawable()
        addView(text(title, 18, true))
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
