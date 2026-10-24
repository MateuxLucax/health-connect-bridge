# Architecture

## System context

Health data travels from the watch to Grafana through two projects: this app
and [health-connect-prometheus-exporter](https://github.com/MateuxLucax/health-connect-prometheus-exporter)
(Go server).

```mermaid
flowchart LR
    watch["Galaxy Watch"] -->|Bluetooth sync| shealth["Samsung Health"]
    shealth -->|writes records| hc["Health Connect<br/>(on-device store)"]
    hc -->|"Changes API / readRecords"| app["health-connect-bridge<br/>(this repo)"]
    app -->|"POST /api/v1/samples<br/>JSON + Bearer token"| exp["health-connect-prometheus-exporter"]
    exp -->|"remote write 1.0<br/>protobuf + snappy"| prom[("Prometheus TSDB")]
    graf["Grafana"] -->|PromQL| prom
```

- The watch syncs to the phone late and in batches, so records reach Health
  Connect minutes or hours after they were measured. Any other app that
  writes to Health Connect works the same way.
- The app only reads Health Connect. It maps records to Prometheus samples
  (metric name, labels, value, original timestamp) and owns the sync
  checkpoint (the Health Connect changes token).
- The exporter validates the samples and writes them to Prometheus with their
  original timestamps. It holds no state.

## Components

```mermaid
flowchart TB
    subgraph ui["UI (Jetpack Compose, Material 3)"]
        main["MainActivity<br/>permission requests + battery intent"]
        appc["App<br/>onboarding or Home/Settings tabs"]
        onb["OnboardingScreen<br/>5 steps"]
        home["HomeScreen<br/>last sync, last error, last values"]
        set["SettingsScreen<br/>server, history import, permissions"]
        ct["ConnectionTest"]
        ds["DeviceStatus<br/>HC availability, permissions, battery"]
    end
    subgraph sync["Sync"]
        wk["SyncWorker<br/>WorkManager, mutex, SyncError mapping"]
        hs["HealthSync<br/>incremental() / backfill()"]
        mt["Metrics.kt<br/>Record.toSamples()"]
        up["Uploader<br/>HttpURLConnection + org.json"]
    end
    subgraph state["State (SharedPreferences 'settings')"]
        st["Settings<br/>URL, token, changes token, status"]
        lv["LastValues<br/>newest value per metric (JSON)"]
    end
    hc["Health Connect"]
    exp["Exporter"]

    main --> appc
    appc --> onb
    appc --> home
    appc --> set
    onb --> ct
    set --> ct
    appc --> ds
    home -->|sync now| wk
    onb -->|"backfill, schedule periodic"| wk
    set -->|backfill| wk
    wk --> hs
    hs --> mt
    hs --> up
    hs --> st
    hs --> lv
    hs --> hc
    ds --> hc
    ct --> up
    up --> exp
    home --> st
    home --> lv
```

| File | Responsibility |
|------|----------------|
| `MainActivity.kt` | Entry point; schedules the periodic job when onboarding is done; launches the Health Connect permission request, the local network permission request (Android 17+) and the battery-optimization exemption; refreshes screens on resume and on preference changes |
| `App.kt` | Shows onboarding until it is finished, then the Home and Settings tabs |
| `OnboardingScreen.kt` | Welcome, server (URL, token, connection test), permissions, battery, history import |
| `HomeScreen.kt` | Last success or last error, "sync now", last value per metric |
| `SettingsScreen.kt` | Server settings and connection test, history import, permission and battery state |
| `ConnectionTest.kt` | Sends an empty batch and reports the outcome |
| `DeviceStatus.kt` | Health Connect availability, granted vs. requested permissions, battery exemption, local network access when the server is local |
| `LocalNetwork.kt` | Whether the server URL is on the local network (resolves the host) and whether local network access is granted (Android 17+) |
| `ServerUrl.kt` | URL validation, cleartext warning, private address ranges |
| `SyncWorker.kt` | WorkManager worker: periodic, sync-now and backfill jobs; records the outcome and returns success, failure or retry |
| `SyncError.kt` | Error kinds (with a `retry` flag) mapped from exceptions; shared by sync and connection test |
| `HealthSync.kt` | Changes API sync, initial reads, backfill, readable types and requested permissions |
| `Metrics.kt` | Synced record types and the record-to-sample mapping |
| `Uploader.kt` | `POST {serverUrl}/api/v1/samples` in chunks of 5,000 |
| `Settings.kt`, `LastValues.kt` | Persisted state |
| `Catalog.kt`, `Format.kt`, `Strings.kt`, `Components.kt`, `Theme.kt` | Data type list for the UI, locale-aware formatting, string resource lookup, shared composables, dynamic color theme |
| `res/values`, `res/values-pt-rBR` | UI text in English (default) and Brazilian Portuguese |
| `PrivacyActivity.kt` | Privacy rationale opened from the Health Connect permission screen |

## Deployment

```mermaid
flowchart LR
    subgraph gh["GitHub"]
        rel["Releases<br/>signed APK per green push to main"]
    end
    subgraph phone["Android phone (minSdk 26)"]
        sh["Samsung Health"] --> hc["Health Connect"]
        hc --> app["health-connect-bridge<br/>sideloaded APK"]
        wm["WorkManager"] --> app
    end
    subgraph server["Home server (Docker Compose)"]
        exp["exporter :8080"]
        prom[("Prometheus")]
        exp --> prom
    end
    rel -.->|manual install| app
    app -->|"HTTP on LAN or HTTPS via reverse proxy"| exp
    graf["Grafana"] --> prom
```

- The Build workflow builds the signed APK, runs the unit tests and the
  emulator end-to-end test on every push to `main`; when all pass, the same
  run publishes it as GitHub Release `v<VERSION>.<run number>` (see
  [ADR 0012](adr/0012-automatic-releases.md)). Manually pushed `v*` tags are
  released the same way. There is no Play Store listing.
- The server side (exporter image, compose file, Prometheus config) is in the
  exporter repo.

## Trust boundaries

```mermaid
flowchart LR
    subgraph device["Phone"]
        hc["Health Connect<br/>per-type read permissions"]
        app["bridge app<br/>URL + token in app-private SharedPreferences"]
        hc -->|"read only, user-granted types"| app
        lnp["Android 17 local network protection<br/>ACCESS_LOCAL_NETWORK"]
        app --> lnp
    end
    subgraph net["Network"]
        wire["HTTP (cleartext allowed) or HTTPS<br/>Bearer token + health data"]
    end
    subgraph host["Home server"]
        exp["exporter<br/>checks the Bearer token"]
        prom["Prometheus<br/>no authentication"]
    end
    lnp --> wire --> exp --> prom
```

| Boundary | Control |
|----------|---------|
| Health Connect to app | The user grants read permission per record type, plus background and history access. The app requests no write permissions in release builds (the debug manifest adds a few write permissions for the emulator test only). |
| App storage | Server URL, token and sync state are in app-private SharedPreferences (`MODE_PRIVATE`). |
| App to exporter | Every request carries `Authorization: Bearer <token>`; the exporter checks it before reading the body and answers `401` otherwise. |
| App to local network | On Android 17+ (the app targets SDK 37) connections to local addresses need the runtime permission `ACCESS_LOCAL_NETWORK` (Nearby devices group); without it they time out. The app declares it and asks only when the server resolves to a local address. The background sync can't ask: it fails with `LOCAL_NETWORK_PERMISSION` instead. See [ADR 0011](adr/0011-local-network-permission.md). |
| Transport | `usesCleartextTraffic="true"` allows plain HTTP for LAN use. Outside a trusted LAN, the exporter must be behind an HTTPS reverse proxy; the app then uses an `https://` URL. |
| Data destination | Data goes only to the configured server. No analytics or third-party SDKs. |
