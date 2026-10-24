package io.github.mateuxlucax.healthconnectbridge.model

/** Latest exported value of one metric (or one sleep stage / exercise type of it). */
class LastValue(val metric: String, val label: String?, val value: Double, val timestampMs: Long)
