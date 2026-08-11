package com.primalsword.priore

import android.content.Context
import org.json.JSONObject
import java.time.Instant

data class SignalSnapshot(
    val kind: String,
    val reason: String,
    val nextTrigger: String,
    val invalidation: String,
    val confirmationPrice: String,
    val invalidationPrice: String,
    val entry: String,
    val stop: String,
    val target: String,
    val trendM15: String,
    val support: String,
    val resistance: String,
    val atrM5: String,
    val timestamp: String,
)

data class MonitorSnapshot(
    val running: Boolean,
    val status: String,
    val latestPrice: String,
    val lastM5Close: String,
    val updatedAt: String,
)

object SignalStore {
    private const val PREFS = "priore_state"
    private const val KEY_LAST_SIGNAL = "last_signal"
    private const val KEY_MONITOR = "monitor"

    fun savePlan(context: Context, plan: TradePlan) {
        val json = JSONObject()
            .put("kind", plan.kind.name)
            .put("reason", plan.reason)
            .put("nextTrigger", plan.nextTrigger)
            .put("invalidation", plan.invalidation)
            .put("confirmationPrice", plan.confirmationPrice?.let(::fmt).orEmpty())
            .put("invalidationPrice", plan.invalidationPrice?.let(::fmt).orEmpty())
            .put("entry", plan.entry?.let(::fmt).orEmpty())
            .put("stop", plan.stop?.let(::fmt).orEmpty())
            .put("target", plan.target?.let(::fmt).orEmpty())
            .put("trendM15", plan.trendM15)
            .put("support", plan.support?.let(::fmt).orEmpty())
            .put("resistance", plan.resistance?.let(::fmt).orEmpty())
            .put("atrM5", plan.atrM5?.let(::fmt).orEmpty())
            .put("timestamp", Instant.now().toString())
        prefs(context).edit().putString(KEY_LAST_SIGNAL, json.toString()).apply()
    }

    fun loadSignal(context: Context): SignalSnapshot? {
        val raw = prefs(context).getString(KEY_LAST_SIGNAL, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            SignalSnapshot(
                kind = json.optString("kind", "WAIT"),
                reason = json.optString("reason"),
                nextTrigger = json.optString("nextTrigger"),
                invalidation = json.optString("invalidation"),
                confirmationPrice = json.optString("confirmationPrice"),
                invalidationPrice = json.optString("invalidationPrice"),
                entry = json.optString("entry"),
                stop = json.optString("stop"),
                target = json.optString("target"),
                trendM15 = json.optString("trendM15"),
                support = json.optString("support"),
                resistance = json.optString("resistance"),
                atrM5 = json.optString("atrM5"),
                timestamp = json.optString("timestamp"),
            )
        }.getOrNull()
    }

    fun saveMonitor(
        context: Context,
        running: Boolean,
        status: String,
        latestPrice: Double? = null,
        lastM5Close: Double? = null,
    ) {
        val previous = loadMonitor(context)
        val json = JSONObject()
            .put("running", running)
            .put("status", status)
            .put("latestPrice", latestPrice?.let(::fmt) ?: previous.latestPrice)
            .put("lastM5Close", lastM5Close?.let(::fmt) ?: previous.lastM5Close)
            .put("updatedAt", Instant.now().toString())
        prefs(context).edit().putString(KEY_MONITOR, json.toString()).apply()
    }

    fun loadMonitor(context: Context): MonitorSnapshot {
        val raw = prefs(context).getString(KEY_MONITOR, null)
            ?: return MonitorSnapshot(false, "Parado", "", "", "")
        return runCatching {
            val json = JSONObject(raw)
            MonitorSnapshot(
                running = json.optBoolean("running", false),
                status = json.optString("status", "Parado"),
                latestPrice = json.optString("latestPrice"),
                lastM5Close = json.optString("lastM5Close"),
                updatedAt = json.optString("updatedAt"),
            )
        }.getOrElse { MonitorSnapshot(false, "Parado", "", "", "") }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun fmt(value: Double): String = "%.2f".format(java.util.Locale.US, value)
}
