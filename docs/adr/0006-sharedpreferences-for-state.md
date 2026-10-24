# 6. SharedPreferences for all state

Status: Accepted

## Context

The app needs to persist: server URL and token, the changes token and the
types it covers, the last success time and error, an onboarding flag, and the
newest value of each metric for the Home screen. Health data itself stays in
Health Connect and Prometheus.

## Decision

Store everything in one private SharedPreferences file, `settings`.
`LastValues` keeps one small JSON object (`last_values`) with the newest value
and timestamp per metric (or per sleep stage / exercise type). No database.

## Consequences

- No schema or migrations; the state is a handful of keys.
- The UI listens to preference changes to refresh.
- `last_values` stays small (one entry per metric and label) and is rewritten
  on each upload.
- Not suitable for storing samples; the app never buffers health data
  itself.
