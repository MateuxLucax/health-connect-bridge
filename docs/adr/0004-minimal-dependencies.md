# 4. Minimal dependencies

Status: Accepted (amended: lifecycle ViewModel and Compose artifacts, see below)

## Context

The app handles health data and is maintained by one person. Every library
adds update work and code that runs with access to that data.

## Decision

Runtime dependencies are limited to Health Connect client, WorkManager,
`activity-compose`, Compose Material 3 and the AndroidX lifecycle Compose
artifacts (`app/build.gradle.kts`). HTTP uses `HttpURLConnection`, JSON uses
the platform's `org.json`. No Room, no DI framework, no networking or
serialization library, no analytics.

### Amendment: lifecycle ViewModel and Compose artifacts

The screens keep their state in AndroidX ViewModels
([ADR 0013](0013-code-structure.md)). That needs two first-party
`androidx.lifecycle` artifacts, pinned to the same version:

- `lifecycle-viewmodel-compose`: `viewModel(factory = ...)` in composables
  and the `viewModelFactory { initializer { } }` builder used by the manual
  DI container.
- `lifecycle-runtime-compose`: `collectAsStateWithLifecycle()`, which stops
  collecting the screens' state while the app is in the background.

Both come from the same project as `activity-compose`, which already pulls
the lifecycle runtime and ViewModel libraries into the APK. Tests also use
`kotlinx-coroutines-test` (test only, not in the APK) to replace
`Dispatchers.Main` in ViewModel tests. Dependency injection stays manual:
no Hilt, Koin or annotation processing.

## Consequences

- Small APK and dependency surface.
- Hand-written request code (`ExporterClient.kt`, about 60 lines) and manual
  JSON building.
- JVM unit tests need the real `org.json` as a test dependency, because
  `android.jar` ships only a stub.
- The lifecycle artifacts must be updated together, and with the Compose
  BOM in mind (they declare a minimum Compose and AGP version).
