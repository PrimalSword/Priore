package com.primalsword.priore

import android.content.Context
import org.json.JSONObject

data class SignalSnapshot(
    val kind: String,
    val reason: String,
    val entry: String,
    val stop: String,
    val target: String,
    val trendM15: String,
    val support: String,
    val resistance: String,
    val atrM5: String,
    val timestamp: String,
)

object SignalStore {
    private const val PREFS = "priore_signals"
    private const val KEY_LAST = "last_signal"

    fun save(context: Context, data: Map<String, String>) {
        val json = JSONObject()
        data.forEach { (key, value) -> json.put(key, value) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST, json.toString())
            .apply()
    }

    fun load(context: Context): SignalSnapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST, null) ?: return null
        val json = JSONObject(raw)
        return SignalSnapshot(
            kind = json.optString("kind", "WAIT"),
            reason = json.optString("reason"),
            entry = json.optString("entry"),
            stop = json.optString("stop"),
            target = json.optString("target"),
            trendM15 = json.optString("trendM15"),
            support = json.optString("support"),
            resistance = json.optString("resistance"),
            atrM5 = json.optString("atrM5"),
            timestamp = json.optString("timestamp"),
        )
    }
}
