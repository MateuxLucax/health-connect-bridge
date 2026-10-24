# Health Connect Bridge

Android app that reads your health data from [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect) in the background and sends it, already mapped to Prometheus metric names, to [health-connect-prometheus-exporter](https://github.com/MateuxLucax/health-connect-prometheus-exporter), which writes it to Prometheus with the original timestamps.

Built for a Galaxy Watch + Samsung Health setup, but it works with any app that writes to Health Connect. Personal, sideloaded app: there is no Play Store listing.

## Architecture

```
Galaxy Watch -> Samsung Health -> Health Connect -> Health Connect Bridge (this app)
    -> POST /api/v1/samples -> health-connect-prometheus-exporter -> Prometheus (remote write)
```

- A WorkManager job runs every 15 minutes when there is network.
- It uses the Health Connect **Changes API**, so records that arrive late (the watch syncs in batches) or are edited are still sent.
- First run (or when the set of granted data types changes): it takes a changes token, reads the last 7 days, uploads them, then stores the token. If the token expires (Health Connect keeps them about 30 days), it reads the last 30 days and starts over.
- The checkpoint only moves forward after the server accepts a batch (2xx). Server errors (5xx) and network errors are retried later. A rejected token (401) or other 4xx is shown in the app.
- Deleted records are ignored: Prometheus cannot delete samples.
- **Backfill N days** re-reads that range and uploads it without touching the checkpoint.

## Phone setup

1. In Samsung Health: **Settings > Health Connect**, and allow Samsung Health to write the data types you want.
2. Install the APK from [Releases](https://github.com/MateuxLucax/health-connect-bridge/releases) (allow installing from unknown sources).
3. Open the app and enter the server URL (for example `http://192.168.0.10:8080`; plain HTTP is allowed for LAN use) and the token. Tap **Salvar**.
4. Tap **Conceder permissões do Health Connect** and allow all data types, plus **access data in the background** and **access past data** (needed to backfill more than 30 days).
5. Tap **Desativar otimização de bateria**. One UI kills background work otherwise.
6. Tap **Sincronizar agora** to check that it works; the status shows the last success or the last error. Use **Importar histórico** to send older data.

## Metrics

Every sample has a `source` label with the package name of the app that wrote the record (for example `com.sec.android.app.shealth`).

| Health Connect record | Metric | Value | Timestamp | Extra labels |
|---|---|---|---|---|
| HeartRateRecord (each sample) | `health_heart_rate_bpm` | beats per minute | sample time | |
| RestingHeartRateRecord | `health_resting_heart_rate_bpm` | beats per minute | time | |
| HeartRateVariabilityRmssdRecord | `health_heart_rate_variability_rmssd_ms` | ms | time | |
| OxygenSaturationRecord | `health_oxygen_saturation_percent` | % | time | |
| RespiratoryRateRecord | `health_respiratory_rate_breaths_per_minute` | breaths/min | time | |
| SkinTemperatureRecord (each delta) | `health_skin_temperature_delta_celsius` | delta in °C | delta time | |
| StepsRecord | `health_steps` | count | start time | |
| DistanceRecord | `health_distance_meters` | meters | start time | |
| ActiveCaloriesBurnedRecord | `health_active_calories_kcal` | kcal | start time | |
| TotalCaloriesBurnedRecord | `health_total_calories_kcal` | kcal | start time | |
| SleepSessionRecord (each stage) | `health_sleep_stage_seconds` | stage duration, s | stage start time | `stage`: awake, sleeping, out_of_bed, light, deep, rem, awake_in_bed, unknown |
| SleepSessionRecord | `health_sleep_session_seconds` | session duration, s | end time | |
| ExerciseSessionRecord | `health_exercise_session_seconds` | session duration, s | start time | `exercise_type`: e.g. `running`, `biking` (the number if unknown) |
| WeightRecord | `health_weight_kg` | kg | time | |
| BodyFatRecord | `health_body_fat_percent` | % | time | |
| Vo2MaxRecord | `health_vo2_max_ml_per_kg_min` | ml/kg/min | time | |

Interval records (steps, distance, calories) are the amount in that interval, stamped at the interval start. The phone does not aggregate; see the exporter's README for how to query them.

Skin temperature is skipped on devices whose Health Connect version doesn't support it.

## Server API

```
POST {serverUrl}/api/v1/samples
Authorization: Bearer <token>
Content-Type: application/json

{"samples":[{"metric":"health_heart_rate_bpm","labels":{"source":"com.sec.android.app.shealth"},"value":72,"timestamp_ms":1792839600000}]}
```

At most 5,000 samples per request.

## Building

GitHub Actions builds a release APK on every push to `main` (downloadable as a workflow artifact). Pushing a tag like `v1.0.0` also publishes `health-connect-bridge-1.0.0.apk` to a GitHub Release. The version name comes from the tag and the version code from the run number.

Locally: `./gradlew assembleRelease` (JDK 17 and the Android SDK are required).

### Release signing

Without signing secrets the APK is signed with a throwaway debug key, which changes between builds, so you would have to uninstall before each update. To sign with your own key, create a keystore once:

```sh
keytool -genkeypair -v -keystore release.jks -alias release \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.base64
```

Then add these repository secrets (**Settings > Secrets and variables > Actions**):

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | contents of `release.jks.base64` |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `release` (the alias above) |
| `SIGNING_KEY_PASSWORD` | key password |

Keep `release.jks` somewhere safe and never commit it.

## License

[MIT](LICENSE)
