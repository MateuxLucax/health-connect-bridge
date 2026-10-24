# Data structures

- [HTTP API contract](#http-api-contract)
- [Metric catalog](#metric-catalog)
- [On-device state](#on-device-state)

## HTTP API contract

This contract is shared by the exporter (`main.go`) and the app
(`Uploader.kt`).

### `POST /api/v1/samples`

Headers: `Authorization: Bearer <AUTH_TOKEN>`, `Content-Type: application/json`.

```json
{
  "samples": [
    {
      "metric": "health_heart_rate_bpm",
      "labels": {"source": "com.sec.android.app.shealth"},
      "value": 72,
      "timestamp_ms": 1792839600000
    }
  ]
}
```

Equivalent JSON Schema (the server checks these rules in code):

```json
{
  "type": "object",
  "required": ["samples"],
  "properties": {
    "samples": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["metric", "value", "timestamp_ms"],
        "properties": {
          "metric": {"type": "string", "pattern": "^[a-zA-Z_:][a-zA-Z0-9_:]*$"},
          "labels": {
            "type": "object",
            "maxProperties": 16,
            "propertyNames": {"pattern": "^[a-zA-Z_][a-zA-Z0-9_]*$", "not": {"pattern": "^__"}},
            "additionalProperties": {"type": "string", "maxLength": 1024}
          },
          "value": {"type": "number"},
          "timestamp_ms": {"type": "integer", "exclusiveMinimum": 0}
        }
      }
    }
  }
}
```

Field rules:

| Field | Rule |
|-------|------|
| body | At most 8 MiB. Exactly one JSON value; trailing data is a `400`. Unknown fields are ignored. A missing or empty `samples` list is valid. |
| `metric` | Prometheus metric name, `^[a-zA-Z_:][a-zA-Z0-9_:]*$`. |
| `labels` | Optional. At most 16. Names match `^[a-zA-Z_][a-zA-Z0-9_]*$` and must not start with `__`. Values at most 1024 bytes. Empty values are dropped (Prometheus treats empty as absent). |
| `value` | Required JSON number (`float64`). |
| `timestamp_ms` | Unix time in milliseconds, `> 0`. Samples more than 1 hour after the server's clock are dropped and logged, not rejected. |
| duplicates | Several samples of the same series with the same timestamp: the last one in the request wins. |

Client-side limits (app): at most 5,000 samples per request, 15 s connect
timeout, 60 s read timeout. The server splits each request into remote write
batches of at most 10,000 points.

### Status codes

| Status | Meaning | What the client does |
|--------|---------|----------------------|
| `204` | Written. Also returned when Prometheus rejected some samples with `400` (too old, duplicate timestamp with a different value): those were dropped for good and the rest written. Also for an empty list (used as a connection test). | Advance the checkpoint. |
| `400` | Malformed JSON, body over 8 MiB, or a sample breaks a rule above. Body: short reason. Nothing was written. | Do not advance; show the error. Retrying the same data will not help. The app ends the run with `Result.failure()` and shows the message. |
| `401` | Missing or wrong token. Nothing was read or written. | Do not advance; show "token rejected". The app ends the run with `Result.failure()`. |
| `502` | Prometheus unreachable, timed out, or answered anything other than 2xx/400 (404 receiver disabled or wrong path, 401/403 from a proxy, 413, 429, 5xx). Some batches may already be written. | Do not advance; retry later with the same data. The app returns `Result.retry()` (WorkManager backoff). |

Other routes answer `404`; a wrong method on a known route answers `405`.

### `GET /healthz`

Unauthenticated. Returns `200` with body `ok`.

## Metric catalog

The app maps Health Connect records to samples (`Metrics.kt` in
health-connect-bridge); the exporter forwards whatever valid samples it
receives. This table is identical in both repositories' docs.

Every sample has a `source` label: the package name of the app that wrote the
record (for example `com.sec.android.app.shealth`). No aggregation happens on
the phone: one record (or one sub-sample, stage or delta of it) becomes one
sample.

| Health Connect record | Metric | Value (unit) | Timestamp | Extra labels |
|---|---|---|---|---|
| `HeartRateRecord` (each sample) | `health_heart_rate_bpm` | beats per minute | sample time | |
| `RestingHeartRateRecord` | `health_resting_heart_rate_bpm` | beats per minute | time | |
| `HeartRateVariabilityRmssdRecord` | `health_heart_rate_variability_rmssd_ms` | ms | time | |
| `OxygenSaturationRecord` | `health_oxygen_saturation_percent` | % | time | |
| `RespiratoryRateRecord` | `health_respiratory_rate_breaths_per_minute` | breaths/min | time | |
| `SkinTemperatureRecord` (each delta) | `health_skin_temperature_delta_celsius` | delta, °C | delta time | |
| `StepsRecord` | `health_steps` | count in interval | start time | |
| `DistanceRecord` | `health_distance_meters` | meters in interval | start time | |
| `ActiveCaloriesBurnedRecord` | `health_active_calories_kcal` | kcal in interval | start time | |
| `TotalCaloriesBurnedRecord` | `health_total_calories_kcal` | kcal in interval | start time | |
| `SleepSessionRecord` (each stage) | `health_sleep_stage_seconds` | stage duration, s | stage start time | `stage`: `awake`, `sleeping`, `out_of_bed`, `light`, `deep`, `rem`, `awake_in_bed`, `unknown` |
| `SleepSessionRecord` | `health_sleep_session_seconds` | session duration, s | end time | |
| `ExerciseSessionRecord` | `health_exercise_session_seconds` | session duration, s | start time | `exercise_type`: lowercase name of the `EXERCISE_TYPE_*` constant (e.g. `running`), or the number if unknown |
| `WeightRecord` | `health_weight_kg` | kg | time | |
| `BodyFatRecord` | `health_body_fat_percent` | % | time | |
| `Vo2MaxRecord` | `health_vo2_max_ml_per_kg_min` | ml/kg/min | time | |

- Interval metrics (steps, distance, calories) are the amount in the
  interval, stamped at the interval start; sum them with `sum_over_time`
  (see [ADR 0009](https://github.com/MateuxLucax/health-connect-prometheus-exporter/blob/main/docs/adr/0009-interval-metrics-at-interval-start.md)).
- `SkinTemperatureRecord` is only read when the device's Health Connect
  supports it (`FEATURE_SKIN_TEMPERATURE`).
- Only types the user granted read permission for are read.

```mermaid
classDiagram
    class Record {
        <<Health Connect>>
        metadata.dataOrigin.packageName
    }
    class Sample {
        metric: String
        labels: Map~String,String~
        value: Number
        timestampMs: Long
    }
    class Labels {
        source: package name
        stage: sleep stage only
        exercise_type: exercise only
    }
    Record "1" --> "0..*" Sample : toSamples()
    Sample *-- Labels
```

## On-device state

Everything is stored in one SharedPreferences file, `settings`
(`MODE_PRIVATE`), through `Settings.kt` and `LastValues.kt`. There is no
database. WorkManager keeps its own job queue.

| Key | Type | Written by | Meaning |
|-----|------|------------|---------|
| `server_url` | String | Onboarding (server step), Settings (save) | Exporter base URL, trimmed. `/api/v1/samples` is appended. |
| `token` | String | Onboarding, Settings | Bearer token, trimmed. |
| `changes_token` | String | `HealthSync` | Health Connect changes token: the sync checkpoint. Saved after the initial read and after each successfully uploaded page of changes. |
| `changes_types` | String | `HealthSync` (with `changes_token`) | Comma-separated, sorted simple class names of the record types the token covers, e.g. `HeartRateRecord,StepsRecord`. If the readable types change, the token is discarded and an initial read starts. |
| `last_success_ms` | Long | `SyncWorker` | Epoch ms of the last successful sync or backfill; `0` = never. |
| `last_error` | String? | `SyncWorker` | Error of the last failed run, shown on the Home screen; cleared on success. |
| `last_values` | String (JSON) | `HealthSync` after each upload | Newest uploaded value per metric, for the Home screen. |
| `onboarding_done` | Boolean | Onboarding (finish) | Shows the main screen and schedules the periodic job. |

`last_values` format: one entry per metric, or per metric and sleep stage or
exercise type (`metric:label`). An entry is replaced only by a sample with an
equal or newer timestamp, so late-arriving old data does not overwrite it.

```json
{
  "health_heart_rate_bpm": {"v": 72.0, "t": 1792839600000},
  "health_sleep_stage_seconds:deep": {"v": 3600.0, "t": 1792800000000},
  "health_exercise_session_seconds:running": {"v": 1800.0, "t": 1792760000000}
}
```

```mermaid
erDiagram
    SETTINGS ||--o| CHANGES_CHECKPOINT : holds
    SETTINGS ||--o{ LAST_VALUE : "last_values JSON"
    SETTINGS {
        string server_url
        string token
        long last_success_ms
        string last_error
        boolean onboarding_done
    }
    CHANGES_CHECKPOINT {
        string changes_token
        string changes_types
    }
    LAST_VALUE {
        string key "metric or metric:label"
        double v "value"
        long t "timestamp ms"
    }
```

### WorkManager jobs

| Unique name | Kind | Policy | Input |
|-------------|------|--------|-------|
| `periodic-sync` | Periodic, 15 min, network required | `KEEP` | none: incremental sync |
| `sync-now` | One-time, network required | `KEEP` | none: incremental sync |
| `backfill` | One-time, network required | `REPLACE` | `backfill_days` = N |
