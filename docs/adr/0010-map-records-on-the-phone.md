# 10. Map records to metrics on the phone, without aggregation

Status: Accepted

## Context

Health Connect records have to become Prometheus samples: metric names,
units, labels and one timestamp each. The exporter is deliberately generic
(see the exporter's
[ADR 0008](https://github.com/MateuxLucax/health-connect-prometheus-exporter/blob/main/docs/adr/0008-app-maps-metrics-server-generic.md)).

## Decision

`RecordMapping.kt` (`data/healthconnect`) maps each record type to samples with a `source` label (the
writing app's package). Series records (heart rate, skin temperature) give
one sample per point; interval records (steps, distance, calories) give the
amount at the interval start; sleep gives one sample per stage plus the
session duration; exercise gives the session duration with an
`exercise_type` label. Nothing is aggregated.

## Consequences

- New metrics need only an app release.
- Totals are computed in PromQL (`sum_over_time`).
- The catalog is documented in both repos ([data.md](../data.md#metric-catalog))
  and checked end to end by the integration test.
