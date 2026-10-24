# 7. Ignore deletions

Status: Accepted

## Context

The Changes API returns `UpsertionChange` and `DeletionChange`. Prometheus
has no way to delete individual samples through remote write.

## Decision

`HealthSync` keeps only `UpsertionChange`s; deletions are skipped.

## Consequences

- A record deleted in Health Connect stays in Prometheus.
- An edited record arrives as an upsert; if its timestamp is unchanged and the
  value differs, Prometheus keeps the old value (duplicate timestamp, dropped
  by the exporter). An edit that changes the timestamp adds a new sample.
- Tested by `deletions_produceNoSamples` and
  `deletionsMixedWithUpserts_onlyUpsertsUploaded`.
