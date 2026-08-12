package com.primalsword.priore

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object PaperTradeStore {
    private const val PREFS = "priore_paper_trading"
    private const val KEY_ACTIVE = "active"
    private const val KEY_HISTORY = "history"
    private const val HISTORY_LIMIT = 300

    fun saveActive(context: Context, trade: PaperTrade?) {
        val editor = prefs(context).edit()
        if (trade == null) editor.remove(KEY_ACTIVE) else editor.putString(KEY_ACTIVE, encode(trade).toString())
        editor.apply()
    }

    fun loadActive(context: Context): PaperTrade? {
        val raw = prefs(context).getString(KEY_ACTIVE, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun finish(context: Context, trade: PaperTrade) {
        saveActive(context, trade)
        val history = historyJson(context)
        history.put(encode(trade))
        val trimmed = JSONArray()
        val start = maxOf(0, history.length() - HISTORY_LIMIT)
        for (index in start until history.length()) trimmed.put(history.getJSONObject(index))
        prefs(context).edit().putString(KEY_HISTORY, trimmed.toString()).apply()
    }

    fun history(context: Context): List<PaperTrade> {
        val array = historyJson(context)
        return buildList {
            for (index in 0 until array.length()) {
                runCatching { decode(array.getJSONObject(index)) }.getOrNull()?.let(::add)
            }
        }.sortedByDescending { it.createdAt }
    }

    private fun historyJson(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }

    private fun encode(trade: PaperTrade): JSONObject = JSONObject()
        .put("id", trade.id)
        .put("signalKind", trade.signalKind.name)
        .put("status", trade.status.name)
        .put("entry", trade.entry)
        .put("stop", trade.stop)
        .put("target", trade.target)
        .put("riskReward", trade.riskReward)
        .put("createdAt", trade.createdAt.toString())
        .put("updatedAt", trade.updatedAt.toString())
        .put("closedAt", trade.closedAt?.toString())
        .put("currentPrice", trade.currentPrice)
        .put("closePrice", trade.closePrice)
        .put("trendM15", trade.trendM15)
        .put("support", trade.support)
        .put("resistance", trade.resistance)
        .put("atrM5", trade.atrM5)
        .put("reason", trade.reason)

    private fun decode(json: JSONObject): PaperTrade = PaperTrade(
        id = json.getString("id"),
        signalKind = SignalKind.valueOf(json.getString("signalKind")),
        status = PaperTradeStatus.valueOf(json.getString("status")),
        entry = json.getDouble("entry"),
        stop = json.getDouble("stop"),
        target = json.getDouble("target"),
        riskReward = json.optDouble("riskReward", 1.8),
        createdAt = Instant.parse(json.getString("createdAt")),
        updatedAt = Instant.parse(json.getString("updatedAt")),
        closedAt = json.optString("closedAt").takeIf { it.isNotBlank() && it != "null" }?.let(Instant::parse),
        currentPrice = json.optNullableDouble("currentPrice"),
        closePrice = json.optNullableDouble("closePrice"),
        trendM15 = json.optString("trendM15", "neutral"),
        support = json.optNullableDouble("support"),
        resistance = json.optNullableDouble("resistance"),
        atrM5 = json.optNullableDouble("atrM5"),
        reason = json.optString("reason"),
    )

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
