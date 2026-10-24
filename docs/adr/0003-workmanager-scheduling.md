# 3. WorkManager: periodic job (15 minutes by default) plus one-time jobs

Status: Accepted (amended: configurable interval)

## Context

Sync must run in the background without the app open, survive reboots,
wait for network, and retry transient failures. 15 minutes is the shortest
interval Android allows for periodic background work.

## Decision

`SyncWorker` (a `CoroutineWorker`) runs all sync work, with a network
constraint; `SyncScheduler` enqueues it:

- `periodic-sync`: unique periodic work every `sync_interval_minutes` (15 by
  default; the user picks 15 or 30 minutes, 1, 3, 6, 12 or 24 hours in the
  onboarding's history step or in Settings). `KEEP` on app start and at the
  end of onboarding, so a launch doesn't restart the timer. When the user
  changes the interval, `UPDATE`: the same unique work gets the new period
  without being cancelled, and its next run follows the new period.
  `SyncScheduler.periodicPlan` holds this decision as a pure function.
- `sync-now`: unique one-time work from the Home screen, `REPLACE`, so a
  previous manual run waiting in retry backoff doesn't swallow the tap
  (replacing a running sync is safe: the checkpoint moves per uploaded page
  and the mutex serializes runs).
- `backfill`: unique one-time work with `backfill_days`, `REPLACE`.

These names, the input key and `SyncWorker`'s class name are stored by
WorkManager on the phone, so they never change (see
[ADR 0013](0013-code-structure.md)).

A process-wide `Mutex` serializes runs. `SyncRunner.runAndReport` maps each exception
to a `SyncError` kind: `failure` for 401, other 4xx, setup problems, a
malformed URL and `SecurityException`; `retry` (WorkManager backoff) for
5xx, network errors and other exceptions. The onboarding asks the user to exempt the app from
battery optimization, since One UI otherwise kills background work.

## Consequences

- Data arrives within roughly the chosen interval (15 minutes by default) plus
  the watch's own sync delay; no real-time streaming. Longer intervals mean
  fewer wake-ups and later data; "Sync now" is always available.
- No foreground service or notification.
- Errors that need the user (bad token, permissions) are not retried in a
  loop; they are shown on the Home screen until the next run succeeds.
