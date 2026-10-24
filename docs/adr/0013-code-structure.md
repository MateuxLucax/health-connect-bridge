# 13. Code structure: layered packages, manual DI and ViewModels

Status: Accepted

## Context

All app code was in one package. Composables read and wrote SharedPreferences
directly, `SyncWorker`'s companion object held scheduling, outcome reporting
and the run mutex, `DeviceStatus` mixed data and loading, `SyncError` held
string resource ids, the record type list existed twice (sync and UI), and
the activity bumped `refreshKey`/`syncKey` counters that every screen
re-read state from. The app is small (one module, three screens, one
maintainer), so the structure should follow Android's architecture guidance
without its large-app machinery.

## Decision

One Gradle module with packages by layer, and features inside `ui/`
(see [architecture](../architecture.md#code-structure)):

- `model`: plain entities (`Sample`, `SyncError`, `SyncState`,
  `BackfillResult`, `LastValue`, `DeviceStatus`). No `Context`, no `R`.
- `data`: repositories and data sources, each a single class (no
  interface/`Impl` pairs): `local` (`SettingsStore`, `LastValues`, flows over
  SharedPreferences), `exporter` (`ExporterClient`), `healthconnect`
  (`RecordMapping`, the single list of synced types and their metrics, and
  `HealthConnectAccess`), `network` (`ServerUrl`, `LocalNetwork`) and
  `DeviceStatusRepository`.
- `sync`: `HealthSync`, `SyncRunner` (mutex, outcome, WorkManager result),
  `SyncScheduler` (unique work), `SyncErrors` (exception to `SyncError`).
- `ui`: `App`, `theme`, `format` (strings, formatting, error messages),
  `components` (shared composables, the catalog, the `ConnectionTest` state
  holder), and `onboarding`, `home`, `settings`, each with its screen and
  ViewModel.
- The root package holds the Android entry points and the wiring:
  `MainActivity`, `PrivacyActivity`, `SyncWorker`, `BridgeApplication`,
  `AppContainer`.

Imports go one way: `model` <- `data` <- `sync` <- `ui` <- root. Only `ui`
(and the root) uses `R` and `BuildConfig`; nothing imports the root package.
`LayerDependencyTest` checks this.

No domain layer or use cases: `HealthSync` is the only complex logic and has
one caller.

**Manual dependency injection.** `BridgeApplication` (the manifest's
`android:name`) creates one `AppContainer`, which builds the stores, the
exporter client, the scheduler, the device status repository and a
`ViewModelProvider.Factory` (`viewModelFactory { initializer { } }`).
`MainActivity` passes the factory to the screens, and `SyncWorker` takes its
dependencies from the container. No DI framework ([ADR 0004](0004-minimal-dependencies.md)).

**ViewModels.** `OnboardingViewModel`, `HomeViewModel` and
`SettingsViewModel` expose one immutable UI state as a `StateFlow`
(`stateIn(WhileSubscribed(5 s))`, collected with
`collectAsStateWithLifecycle`) and plain methods; one-off feedback (toasts)
stays in the composable. They get no `Context`: the scheduler is passed as
functions, so JVM tests use the existing fakes. The stores' flows are
`callbackFlow`s around `OnSharedPreferenceChangeListener`, so a sync written
by `SyncWorker` shows up without counters. Form fields and the onboarding
step stay in `rememberSaveable`; the device status is still reloaded in
`App` on resume and after permission requests (`refreshKey`), because it
depends on the activity lifecycle. Permission requests stay in
`MainActivity` and reach the screens as `AppActions`.

**Names that never change.** WorkManager stores the worker's fully
qualified class name with every job (the periodic job is kept indefinitely)
and creates it by reflection. So `io.github.mateuxlucax.healthconnectbridge.SyncWorker`
stays in the root package as a thin class delegating to `sync/`, and
`SyncScheduler` gets the class injected. `MainActivity` (launcher shortcuts)
and `PrivacyActivity` (target of the `ViewPermissionUsageActivity` alias)
keep their names too, as do the preferences file `settings`, every
preference key, the unique work names and the `backfill_days` input key.

## Consequences

- Screens get data only through their ViewModel; data classes are testable
  without Android, and ViewModel tests need only `kotlinx-coroutines-test`.
- Moving `SyncWorker` later would need a class kept at the old name or a
  custom `WorkerFactory` (with `Configuration.Provider` and the default
  initializer removed); neither is worth a cosmetic move.
- Minification stays off: the record mapping and the test string lookup use
  reflection.
- Not adopted: multiple modules, Hilt or Koin, Navigation Compose, use
  cases, DTO and mapper classes, `util`/`common` packages.
