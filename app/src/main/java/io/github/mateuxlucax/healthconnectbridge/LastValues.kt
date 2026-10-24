package io.github.mateuxlucax.healthconnectbridge

import android.content.SharedPreferences
import org.json.JSONObject

/** Latest exported value of one metric (or one sleep stage / exercise type of it). */
class LastValue(val metric: String, val label: String?, val value: Double, val timestampMs: Long)

/** Newest uploaded value per metric, kept as a small JSON object in SharedPreferences. */
class LastValues(private val prefs: SharedPreferences) {

    fun record(samples: List<Sample>) {
        if (samples.isEmpty()) return
        val json = load()
        for (s in samples) {
            val key = keyOf(s)
            val current = json.optJSONObject(key)
            if (current == null || current.getLong("t") <= s.timestampMs) {
                json.put(key, JSONObject().put("v", s.value.toDouble()).put("t", s.timestampMs))
            }
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun all(): List<LastValue> {
        val json = load()
        return json.keys().asSequence().mapNotNull { key ->
            val entry = json.optJSONObject(key) ?: return@mapNotNull null
            val metric = key.substringBefore(SEPARATOR)
            val label = key.substringAfter(SEPARATOR, "").ifEmpty { null }
            runCatching { LastValue(metric, label, entry.getDouble("v"), entry.getLong("t")) }.getOrNull()
        }.toList()
    }

    /** The stored JSON, or an empty object when it is missing or corrupt: this is only a display cache. */
    private fun load() = prefs.getString(KEY, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    private fun keyOf(s: Sample): String {
        val label = s.labels["stage"] ?: s.labels["exercise_type"]
        return if (label == null) s.metric else s.metric + SEPARATOR + label
    }

    private companion object {
        const val KEY = "last_values"
        const val SEPARATOR = ":"
    }
}
