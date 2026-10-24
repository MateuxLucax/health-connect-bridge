# 2. Changes API, checkpoint advanced only after a 2xx

Status: Accepted

## Context

Records reach Health Connect late (the watch syncs in batches) and can be
edited. Reading "the last N minutes" on each run would miss late records or
resend everything. The exporter is stateless and answers `502` when it could
not write to Prometheus, so the app must not lose data on failure.

## Decision

`HealthSync.incremental()` uses the Health Connect Changes API:

- First run, or when the set of readable types changes: take a changes token
  **first**, read the last 7 days with `readRecords` (paged), upload, then
  store the token. Changes made during the read are picked up next time.
- Then loop `getChanges(token)` while `hasMore`, upload the upserted records,
  and save `nextChangesToken` only after the upload got a 2xx.
- If the token expired, read the last 30 days and start over with a new token.
- The token is stored with the list of record types it covers
  (`changes_types`).

## Consequences

- Late and edited records are sent; nothing is skipped after a failure.
- Delivery is at least once: a failure mid-run or a re-read resends samples.
  Prometheus keeps the stored value per timestamp, so this is harmless.
- Gaps longer than about 30 days without a successful sync are only partly
  recovered automatically; older data needs a backfill and must fit
  Prometheus' out-of-order window.
- Covered by `HealthSyncTest` (token taken first, stored after uploads, kept
  on mid-loop failure, expiry, type changes).
