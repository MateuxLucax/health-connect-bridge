# Architecture Decision Records

Short records of the decisions behind the app (Status, Context, Decision,
Consequences). The server's decisions are in the
[exporter ADRs](https://github.com/MateuxLucax/health-connect-prometheus-exporter/tree/main/docs/adr).

| # | Decision | Status |
|---|----------|--------|
| [0001](0001-health-connect-with-own-minimal-app.md) | Read from Health Connect with our own minimal app | Accepted |
| [0002](0002-changes-api-checkpoint-after-2xx.md) | Changes API, checkpoint advanced only after a 2xx | Accepted |
| [0003](0003-workmanager-scheduling.md) | WorkManager: 15-minute periodic job plus one-time jobs | Accepted |
| [0004](0004-minimal-dependencies.md) | Minimal dependencies | Accepted (amended for lifecycle ViewModel) |
| [0005](0005-compose-material3-dynamic-color.md) | Jetpack Compose and Material 3 with dynamic color | Accepted |
| [0006](0006-sharedpreferences-for-state.md) | SharedPreferences for all state | Accepted |
| [0007](0007-ignore-deletions.md) | Ignore deletions | Accepted |
| [0008](0008-sideloaded-apk-github-releases.md) | Sideloaded APK distributed through GitHub Releases | Accepted |
| [0009](0009-testing-strategy.md) | Testing strategy: JVM tests with fakes plus a real end-to-end test | Accepted |
| [0010](0010-map-records-on-the-phone.md) | Map records to metrics on the phone, without aggregation | Accepted |
| [0011](0011-local-network-permission.md) | Ask for the local network permission when the server is local | Accepted |
| [0012](0012-automatic-releases.md) | Release automatically on every green push to main | Accepted |
| [0013](0013-code-structure.md) | Code structure: layered packages, manual DI and ViewModels | Accepted |
