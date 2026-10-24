# 9. Testing strategy: JVM tests with fakes plus a real end-to-end test

Status: Accepted

## Context

The sync logic has subtle rules (token taken first, checkpoint only after a
2xx, expiry, type changes, error mapping) and the metric mapping must match
what ends up in Prometheus. Health Connect only exists on a device.

## Decision

- JVM unit tests (`app/src/test`) specify the behavior with fakes: a fake
  `HealthConnectClient`, in-memory SharedPreferences and a local HTTP server
  (`Fakes.kt`). They cover `HealthSync`, `Metrics`, `Uploader`,
  `SyncWorker` result mapping, `LastValues` and formatting. They run in the
  Build workflow.
- The Integration workflow runs `EndToEndTest` on an Android emulator (API
  36): it inserts records into the real Health Connect, runs the app's sync
  against the real exporter (built from its repo) and a real Prometheus 3.5
  with the exporter's `prometheus.yml`, and checks every series in Prometheus
  against the metric catalog, including late records through the Changes API
  and a rejected token. It also takes the README screenshots.

## Consequences

- Fast feedback on logic without a device; real confidence in the full path.
- A change in the exporter's contract breaks the bridge's integration run.
- The emulator job is slow (up to 60 minutes timeout) and runs only on
  pushes to `main`.
