# 3. WorkManager: 15-minute periodic job plus one-time jobs

Status: Accepted

## Context

Sync must run in the background without the app open, survive reboots,
wait for network, and retry transient failures. 15 minutes is the shortest
interval Android allows for periodic background work.

## Decision

`SyncWorker` (a `CoroutineWorker`) runs all sync work, with a network
constraint:

- `periodic-sync`: unique periodic work every 15 minutes, `KEEP`.
- `sync-now`: unique one-time work from the Home screen, `KEEP`.
- `backfill`: unique one-time work with `backfill_days`, `REPLACE`.

A process-wide `Mutex` serializes runs. `runAndReport` maps each exception
to a `SyncError` kind: `failure` for 401, other 4xx, setup problems, a
malformed URL and `SecurityException`; `retry` (WorkManager backoff) for
5xx, network errors and other exceptions. The onboarding asks the user to exempt the app from
battery optimization, since One UI otherwise kills background work.

## Consequences

- Data arrives within roughly 15 minutes plus the watch's own sync delay; no
  real-time streaming.
- No foreground service or notification.
- Errors that need the user (bad token, permissions) are not retried in a
  loop; they are shown on the Home screen until the next run succeeds.
