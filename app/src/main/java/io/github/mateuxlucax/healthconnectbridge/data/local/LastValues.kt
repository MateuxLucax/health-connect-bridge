package io.github.mateuxlucax.healthconnectbridge.data.local

import android.content.Context
import android.content.SharedPreferences
import io.github.mateuxlucax.healthconnectbridge.model.LastValue
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import org.json.JSONObject

/** Newest uploaded value per metric, kept as a small JSON object in the same SharedPreferences as [Settings]. */
class LastValues(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(Settings.open(context))

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

    companion object {
        internal const val KEY = "last_values"
        private const val SEPARATOR = ":"
    }
}
