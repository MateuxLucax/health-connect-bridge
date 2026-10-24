# 4. Minimal dependencies

Status: Accepted

## Context

The app handles health data and is maintained by one person. Every library
adds update work and code that runs with access to that data.

## Decision

Runtime dependencies are limited to Health Connect client, WorkManager,
`activity-compose` and Compose Material 3 (`app/build.gradle.kts`). HTTP uses
`HttpURLConnection`, JSON uses the platform's `org.json`. No Room, no DI
framework, no networking or serialization library, no analytics.

## Consequences

- Small APK and dependency surface.
- Hand-written request code (`Uploader.kt`, about 60 lines) and manual JSON
  building.
- JVM unit tests need the real `org.json` as a test dependency, because
  `android.jar` ships only a stub.
