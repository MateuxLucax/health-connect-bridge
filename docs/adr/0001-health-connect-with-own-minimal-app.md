# 1. Read from Health Connect with our own minimal app

Status: Accepted

## Context

The goal is to get Galaxy Watch data (heart rate, steps, sleep, ...) into
Prometheus with original timestamps. Options considered:

- Samsung Health cloud API: Samsung Health offers no cloud API for third
  parties.
- Samsung Health Data SDK: reads Samsung Health directly, but requires
  partner approval from Samsung.
- Health Connect: Android's on-device health store. Samsung Health writes to
  it, and any app can read it with user-granted permissions.
- The third-party "HC Webhook" app, which reads Health Connect and posts to a
  webhook: it has a large feature surface, embeds a closed-source FeedbackJar
  SDK, and has a single maintainer. It would sit between the user's health
  data and the network.

## Decision

Read from Health Connect with a purpose-built app that does one thing: read
the configured record types and POST them to the user's own exporter.

## Consequences

- Works with any app that writes to Health Connect, not only Samsung Health.
- Small, auditable code base with no third-party SDKs; data goes only to the
  configured server.
- Bound by Health Connect's model: per-type permissions, background and
  history permissions, changes tokens that expire after about 30 days, rate
  limits.
- We maintain the app ourselves.
