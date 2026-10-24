# Sequences

- [Onboarding](#onboarding)
- [First sync](#first-sync)
- [Incremental sync](#incremental-sync)
- [Changes token expired](#changes-token-expired)
- [Error handling](#error-handling)
- [Backfill (history import)](#backfill-history-import)
- [Connection test](#connection-test)
- [End to end: app, exporter, Prometheus](#end-to-end-app-exporter-prometheus)

All sync work runs in `SyncWorker` (WorkManager, network required). A
process-wide mutex makes jobs run one at a time.

## Onboarding

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant O as OnboardingScreen
    participant SP as Settings (SharedPreferences)
    participant HC as Health Connect
    participant OS as Android settings
    participant WM as WorkManager

    U->>O: open app (onboarding_done = false)
    O->>U: step 1 Welcome
    U->>O: step 2 Server: URL + token
    opt connection test
        O->>O: ConnectionTest (see below)
    end
    O->>SP: save server_url, token (on Next)
    U->>O: step 3 Permissions
    O->>HC: request read permissions for supported types,<br/>plus background and history if available
    HC-->>O: granted set
    Note over O: Next enabled only with at least one read permission
    U->>O: step 4 Battery (optional)
    O->>OS: ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    U->>O: step 5 History (optional)
    opt import N days
        O->>WM: enqueue unique "backfill" (REPLACE, N days)
    end
    U->>O: Finish
    O->>SP: onboarding_done = true
    O->>WM: enqueue unique periodic "periodic-sync" (15 min, KEEP)
```

`MainActivity` also calls `schedulePeriodic` on every launch after
onboarding; `KEEP` leaves an existing schedule alone.

## First sync

No changes token is stored yet, or the set of readable record types changed
(the stored `changes_types` differs).

```mermaid
sequenceDiagram
    autonumber
    participant W as SyncWorker
    participant S as HealthSync
    participant HC as Health Connect
    participant U as Uploader
    participant E as Exporter
    participant SP as SharedPreferences

    W->>S: incremental()
    S->>HC: granted permissions, supported features
    S->>S: readable types, typesKey = sorted type names
    S->>SP: changes_token / changes_types
    SP-->>S: none, or types differ
    S->>HC: getChangesToken(types)
    HC-->>S: token T0 (taken first: changes during the read are not missed)
    loop each type
        loop pages until pageToken is empty
            S->>HC: readRecords(type, last 7 days, pageToken)
            HC-->>S: records, pageToken
            S->>S: toSamples()
            S->>U: upload(samples)
            U->>E: POST /api/v1/samples (chunks of 5,000)
            E-->>U: 204
            S->>SP: last_values
        end
    end
    S->>SP: save changes_token = T0, changes_types
    Note over S: then continues with the incremental loop from T0
```

If any upload fails, the exception propagates before `T0` is saved, so the
next run starts the initial read again.

## Incremental sync

```mermaid
sequenceDiagram
    autonumber
    participant W as SyncWorker
    participant S as HealthSync
    participant HC as Health Connect
    participant U as Uploader
    participant E as Exporter
    participant SP as SharedPreferences

    W->>S: incremental()
    S->>SP: changes_token T (types match)
    loop until hasMore is false
        S->>HC: getChanges(T)
        HC-->>S: changes, nextChangesToken T', hasMore
        S->>S: keep UpsertionChange only (deletions ignored), toSamples()
        S->>U: upload(samples) (skipped when empty)
        U->>E: POST /api/v1/samples
        E-->>U: 2xx
        S->>SP: last_values
        S->>SP: save changes_token = T' (only after 2xx)
        S->>S: T = T'
    end
    S-->>W: return
    W->>SP: last_success_ms = now, last_error = null
    W-->>W: Result.success()
```

A failure in the middle of the loop keeps the last token saved after a
successful page; the next run resumes from there.

## Changes token expired

Health Connect keeps changes tokens for about 30 days.

```mermaid
sequenceDiagram
    autonumber
    participant S as HealthSync
    participant HC as Health Connect
    participant E as Exporter
    participant SP as SharedPreferences

    S->>HC: getChanges(T)
    HC-->>S: changesTokenExpired = true
    S->>HC: getChangesToken(types)
    HC-->>S: new token T0
    loop each type, each page
        S->>HC: readRecords(type, last 30 days)
        HC-->>S: records
        S->>E: POST /api/v1/samples
        E-->>S: 204
    end
    S->>SP: save changes_token = T0
    S->>HC: getChanges(T0)
    Note over S,HC: normal incremental loop continues
```

Samples already in Prometheus are sent again; Prometheus keeps the stored
value, so the re-read is harmless.

## Error handling

`SyncWorker.runAndReport` turns any exception into a `SyncError` (a stable
kind plus a detail), stores it in `last_error` / `last_error_detail` for the
Home screen, and returns `retry` when the kind's `retry` flag is set,
otherwise `failure`.

```mermaid
sequenceDiagram
    autonumber
    participant WM as WorkManager
    participant W as SyncWorker
    participant S as HealthSync
    participant E as Exporter
    participant SP as SharedPreferences

    WM->>W: doWork()
    W->>S: incremental() or backfill(n)
    S->>E: POST /api/v1/samples
    alt 2xx
        E-->>S: 204
        W->>SP: last_success_ms = now, last_error = null
        W-->>WM: success
    else 401
        E-->>S: 401
        W->>SP: last_error = UNAUTHORIZED
        W-->>WM: failure (no retry, user must fix the token)
    else other 4xx (e.g. 400)
        E-->>S: 4xx + reason
        W->>SP: last_error = REJECTED + detail
        W-->>WM: failure
    else 5xx (502: Prometheus down or misconfigured)
        E-->>S: 502
        W->>SP: last_error = SERVER + detail
        W-->>WM: retry (backoff)
    else network error (IOException)
        S--xE: connect/read failure
        W->>SP: last_error = NETWORK + detail
        W-->>WM: retry (backoff)
    end
    Note over S,SP: on every failure the changes token stays at the last 2xx
```

Other outcomes: missing URL or token, Health Connect unavailable, no read
permission (`SetupException`), a malformed URL and `SecurityException` end
with `failure`; any other exception (for example Health Connect rate
limiting) ends with `retry`. A `failure` ends that run only; the periodic job runs again at its
next interval.

## Backfill (history import)

Started from onboarding or Settings with a number of days (default 30).

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant WM as WorkManager
    participant W as SyncWorker
    participant S as HealthSync
    participant HC as Health Connect
    participant E as Exporter
    participant P as Prometheus

    U->>WM: import N days: unique "backfill" job (REPLACE)
    WM->>W: doWork(backfill_days = N)
    W->>S: backfill(N)
    loop each readable type, each page
        S->>HC: readRecords(type, now - N days .. now)
        HC-->>S: records
        S->>E: POST /api/v1/samples
        E->>P: remote write
        P-->>E: 204, or 400 too old (dropped)
        E-->>S: 204
    end
    Note over S: changes token is not read or written
    W->>W: record success or error as usual
```

Samples older than Prometheus' out-of-order window (30 days in the exporter's
example config) are rejected by Prometheus and dropped by the exporter; the
request still succeeds.

## Connection test

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant C as ConnectionTest
    participant U2 as Uploader.check
    participant E as Exporter

    U->>C: test (URL, token from the form, trimmed)
    C->>U2: check(url, token)
    U2->>E: POST /api/v1/samples {"samples":[]}
    alt 204
        E-->>U2: 204
        C-->>U: OK, server accepted the token
    else 401
        E-->>U2: 401
        C-->>U: SyncError UNAUTHORIZED
    else other status
        E-->>U2: status + body
        C-->>U: SyncError REJECTED or SERVER
    else malformed URL or network error
        C-->>U: SyncError INVALID_URL or NETWORK
    end
```

The empty batch reaches neither Health Connect nor Prometheus, and the form
values are not saved by the test.

## End to end: app, exporter, Prometheus

The same diagram appears in the
[exporter docs](https://github.com/MateuxLucax/health-connect-prometheus-exporter/blob/main/docs/sequences.md#end-to-end-app-exporter-prometheus).

```mermaid
sequenceDiagram
    autonumber
    participant WM as WorkManager
    participant S as HealthSync (app)
    participant HC as Health Connect
    participant SP as SharedPreferences
    participant E as Exporter
    participant P as Prometheus

    WM->>S: periodic job (every 15 min, network required)
    S->>SP: read changes token + granted types key
    S->>HC: getChanges(token)
    HC-->>S: upserted records, nextChangesToken, hasMore
    S->>S: map records to samples (metric, labels, value, timestamp_ms)
    loop chunks of at most 5,000 samples
        S->>E: POST /api/v1/samples (Bearer token)
        E->>E: auth, validate, drop future, group, batch
        E->>P: remote write (protobuf + snappy)
        alt Prometheus 2xx or 400 (rejected samples dropped)
            P-->>E: 204 / 400
            E-->>S: 204
        else unreachable or other status
            P-->>E: error
            E-->>S: 502
            S-->>WM: HttpStatusException(502), SyncWorker returns Result.retry()
        end
    end
    S->>SP: save nextChangesToken (only after every chunk got 2xx)
    S->>SP: last values, last success time
    Note over S,HC: repeat getChanges while hasMore
```
