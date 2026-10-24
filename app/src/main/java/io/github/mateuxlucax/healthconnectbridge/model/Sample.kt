package io.github.mateuxlucax.healthconnectbridge.model

/** One Prometheus sample, as sent to the exporter. */
class Sample(
    val metric: String,
    val labels: Map<String, String>,
    val value: Number,
    val timestampMs: Long,
)
