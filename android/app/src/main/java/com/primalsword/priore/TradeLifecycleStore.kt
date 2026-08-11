package com.primalsword.priore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object TradeLifecycleStore {
    private const val PREFS = "priore_trade_lifecycle"
    private const val KEY_ACTIVE = "active_setup"
    private const val KEY_HISTORY = "setup_history"
    private const val KEY_AUTO_DEMO = "auto_demo_execution"
    private const val HISTORY_LIMIT = 100

    fun isAutoDemoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DEMO, false)

    fun setAutoDemoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DEMO, enabled).apply()
    }

    fun saveActive(context: Context, setup: ActiveSetup?) {
        val editor = prefs(context).edit()
        if (setup == null) {
            editor.remove(KEY_ACTIVE)
        } else {
            editor.putString(KEY_ACTIVE, encode(setup).toString())
        }
        editor.apply()
    }

    fun loadActive(context: Context): ActiveSetup? {
        val raw = prefs(context).getString(KEY_ACTIVE, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun appendHistory(context: Context, setup: ActiveSetup) {
        val array = loadHistoryJson(context)
        array.put(encode(setup))
        val trimmed = JSONArray()
        val from = maxOf(0, array.length() - HISTORY_LIMIT)
        for (index in from until array.length()) trimmed.put(array.getJSONObject(index))
        prefs(context).edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun history(context: Context): List<ActiveSetup> {
        val array = loadHistoryJson(context)
        return buildList {
            for (index in 0 until array.length()) {
                runCatching { decode(array.getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }.sortedByDescending { it.updatedAt }
    }

    private fun loadHistoryJson(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun encode(setup: ActiveSetup): JSONObject = JSONObject()
        .put("setupId", setup.setupId)
        .put("signalKind", setup.signalKind.name)
        .put("status", setup.status.name)
        .put("signalEntry", setup.signalEntry)
        .put("stop", setup.stop)
        .put("target", setup.target)
        .put("riskReward", setup.riskReward)
        .put("createdAt", setup.createdAt.toString())
        .put("updatedAt", setup.updatedAt.toString())
        .put("actualEntry", setup.actualEntry)
        .put("currentPrice", setup.currentPrice)
        .put("positionId", setup.positionId)
        .put("orderId", setup.orderId)
        .put("volume", setup.volume)
        .put("closePrice", setup.closePrice)
        .put("grossProfit", setup.grossProfit)
        .put("note", setup.note)

    private fun decode(json: JSONObject): ActiveSetup = ActiveSetup(
        setupId = json.getString("setupId"),
        signalKind = SignalKind.valueOf(json.getString("signalKind")),
        status = SetupStatus.valueOf(json.getString("status")),
        signalEntry = json.getDouble("signalEntry"),
        stop = json.getDouble("stop"),
        target = json.getDouble("target"),
        riskReward = json.optDouble("riskReward", 1.8),
        createdAt = Instant.parse(json.getString("createdAt")),
        updatedAt = Instant.parse(json.getString("updatedAt")),
        actualEntry = json.optNullableDouble("actualEntry"),
        currentPrice = json.optNullableDouble("currentPrice"),
        positionId = json.optNullableLong("positionId"),
        orderId = json.optNullableLong("orderId"),
        volume = json.optNullableLong("volume"),
        closePrice = json.optNullableDouble("closePrice"),
        grossProfit = json.optNullableDouble("grossProfit"),
        note = json.optString("note"),
    )

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else when (val value = opt(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
