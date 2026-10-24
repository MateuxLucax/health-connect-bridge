# 14. Skip records Health Connect can't read

Status: Accepted

## Context

The Health Connect client library (`androidx.health.connect:connect-client`) builds a record object for every
record it returns and validates it in `init`, for example
`require(startTime.isBefore(endTime)) { "startTime must be before endTime." }` in `StepsRecord`,
`SleepSessionRecord` and `ExerciseSessionRecord`, plus stage, segment and lap bounds for sessions. The
platform stores records that fail these checks (seen with Samsung Health data on a Galaxy S23+, Android 17),
so `readRecords` and `getChanges` throw `IllegalArgumentException` for the whole page that contains one.

With one such record, every history import failed, and an incremental sync whose changes contained it could
never advance its checkpoint: every run failed with "unexpected error" and retried forever.

## Decision

- An `IllegalArgumentException` from `readRecords` means "a record in this window can't be read". `HealthSync`
  splits the window of that type in halves and reads each, recursively, uploading everything readable. A failing
  window of at most one minute is skipped. After 100 failed reads for one type in one range, failing windows
  are skipped without further splitting, so the number of reads stays bounded.
- An `IllegalArgumentException` from `getChanges` can't be read around. `HealthSync` takes a new changes token,
  reads (with the splitting above) everything since the last saved checkpoint (7 to 30 days, as when the
  record types change), saves the new token and continues. If the new token's changes fail too in the same
  run, the run ends as a success with one more skip; the next run tries again.
- Skips never fail a run. Their count is stored with the run's outcome and shown as a secondary line on Home
  (incremental sync) or under Import history.
- Every other exception keeps failing or retrying the run as before.

## Consequences

- One bad record costs at most one minute of that record type (in practice, just the bad record) instead of
  the whole sync. A bad record spanning a long time, or many bad records, can cost more of that type, bounded by
  the read budget.
- The Changes API fallback re-sends up to 30 days of samples; Prometheus keeps the stored values, so this is
  harmless (as for an expired token).
- The skipped records never reach Prometheus. The app can't fix them; the app that wrote them has to.
- Tested in `HealthSyncTest` (bad record in a backfill and a first run, bounded bisection, `getChanges`
  fallback without loops, other errors still failing) and `SyncRunnerTest` (skips reported, not failing).
