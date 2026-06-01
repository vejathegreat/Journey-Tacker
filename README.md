# London Live Bus Journey Tracker

A native Android application built with **Jetpack Compose** and **Material 3** that lets users plan bus journeys across London and track live buses on a selected route. Data is sourced from the [TfL Unified API](https://api.tfl.gov.uk/).

This document describes the **engineering approach**, **architecture**, **local setup**, **API key configuration**, and **trade-offs** made during implementation.

---

## Table of contents

1. [Approach](#approach)
2. [Architecture](#architecture)
3. [Modules](#modules)
4. [Libraries & dependencies](#libraries--dependencies)
5. [The virtual GPS problem](#the-virtual-gps-problem)
6. [Setup](#setup)
7. [API key configuration](#api-key-configuration)
8. [Trade-offs](#trade-offs)
9. [TfL endpoints used](#tfl-endpoints-used)
10. [Tech stack](#tech-stack)

---

## Approach

The assignment centres on a systems problem: **TfL does not expose live bus latitude/longitude**. The app must infer where a bus is by joining three independent datasets in memory:

| Dataset | TfL endpoint | Role |
|---------|--------------|------|
| Journey planning | `Journey/JourneyResults` | User picks a **bus line** to track |
| Live arrivals | `Line/{lineId}/Arrivals` | **Which stop** each bus is approaching (`naptanId`, `timeToStation`) |
| Route geometry | `Line/{lineId}/Route/Sequence/{direction}` | **Lat/lon** for each stop along the route |

The implementation prioritises:

- **Correctness of data joins** over visual polish
- **Clear layer separation** (network DTOs → domain models → UI state)
- **Lifecycle-aware polling** (no background work when the tracking screen is not visible)
- **Graceful ambiguity handling** (location search and journey API disambiguation)

The user flow is intentionally simple:

1. Enter origin and destination → resolve locations → plan journey
2. Tap a **bus leg** on a journey card
3. View **live arrivals** (list) and **inferred positions** (map), refreshed every 30 seconds

---

## Architecture

### Pattern: multi-module Clean Architecture + MVVM

```
┌─────────────────────────────────────────────────────────────┐
│  :app                                                       │
│  Hilt Application · NavHost · BuildConfig / manifest keys   │
└──────────────────────────┬──────────────────────────────────┘
                           │
         ┌─────────────────┴─────────────────┐
         ▼                                   ▼
┌─────────────────────┐           ┌─────────────────────┐
│  :feature:journey   │           │  :feature:tracking  │
│  JourneyScreen      │           │  TrackingScreen     │
│  JourneyViewModel   │           │  TrackingViewModel  │
└──────────┬──────────┘           └──────────┬──────────┘
           │                                  │
           └─────────────────┬────────────────┘
                             ▼
                   ┌─────────────────────┐
                   │  :core:data         │
                   │  Repositories       │
                   │  Mappers (joins)    │
                   └──────────┬──────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │ :core:model│  │:core:network│  │:core:common│
     │ Domain     │  │ Retrofit   │  │ Dispatchers│
     │ interfaces │  │ DTOs       │  │            │
     └────────────┘  └────────────┘  └────────────┘
```

### Module responsibilities

| Module | Responsibility |
|--------|----------------|
| `:app` | Application entry, Hilt wiring, `JourneyTrackerNavHost`, secrets via `local.properties` |
| `:core:model` | Domain types (`Location`, `PlannedJourney`, `VehiclePosition`, …) and repository contracts |
| `:core:network` | `TflApi` (Retrofit), kotlinx-serialization DTOs, HTTP → user-facing error mapping |
| `:core:data` | `JourneyRepositoryImpl`, `TrackingRepositoryImpl`, DTO→domain mappers, **virtual GPS join** |
| `:core:common` | `DispatcherProvider` (IO/Main injection) |
| `:core:designsystem` | Material 3 theme (colour, typography) |
| `:feature:journey` | Journey planning UI, search suggestions, disambiguation dialogs |
| `:feature:tracking` | Google Map, route polyline, live bus list, polling UI state |

### Data flow

**Journey planning (request/response):**

```
Compose UI → JourneyViewModel (StateFlow)
          → JourneyRepository
          → TflApi (Retrofit, IO dispatcher)
          → Mapper → domain Result / JourneyPlanResult
          → UI state update
```

**Live tracking (stream + poll):**

```
Compose UI → TrackingViewModel (stateIn + WhileSubscribed)
          → TrackingRepository.observeTracking()  [Flow]
          → loop: fetch arrivals + join cached route stops → emit Result
          → delay(30_000) while collector active
```

When the user navigates away from the tracking screen, `SharingStarted.WhileSubscribed(5_000)` stops collecting; the repository’s `flow { while (isActive) … }` is cancelled and **polling stops**.

### State management

- **ViewModels** expose immutable `StateFlow` / `Flow` of screen-specific UI state data classes.
- **Repositories** return `Result<T>` for one-shot operations and `Flow<Result<Snapshot>>` for polling.
- **No** global singleton UI store; each feature owns its state.
- **Compose** uses `collectAsStateWithLifecycle()` to respect lifecycle.

### Dependency injection

[Hilt](https://developer.android.com/training/dependency-injection/hilt-android) provides:

- `NetworkModule` — OkHttp, Retrofit, `TflApi`
- `DataModule` — repository bindings
- `AppModule` — `@TflAppKey` from `BuildConfig.TFL_APP_KEY`

---

## Modules

Eight Gradle modules. Dependency direction flows **inward** (features → data → network/model → common).

| Module | Type | Namespace / package | Depends on |
|--------|------|---------------------|------------|
| `:app` | Application | `com.velaphi.journeytracker` | `:core:designsystem`, `:core:data`, `:core:network`, `:feature:journey`, `:feature:tracking` |
| `:core:common` | JVM library | `com.velaphi.journeytracker.core.common` | — |
| `:core:model` | JVM library | `com.velaphi.journeytracker.core.model` | `:core:common` |
| `:core:network` | Android library | `com.velaphi.journeytracker.core.network` | `:core:model` |
| `:core:data` | Android library | `com.velaphi.journeytracker.core.data` | `:core:common`, `:core:model`, `:core:network` |
| `:core:designsystem` | Android library | `com.velaphi.journeytracker.core.designsystem` | — (Compose theme only) |
| `:feature:journey` | Android library | `com.velaphi.journeytracker.feature.journey` | `:core:common`, `:core:model`, `:core:data`, `:core:designsystem` |
| `:feature:tracking` | Android library | `com.velaphi.journeytracker.feature.tracking` | `:core:common`, `:core:model`, `:core:data`, `:core:designsystem` |

### Module roles (summary)

- **`:app`** — `Application`, `MainActivity`, `JourneyTrackerNavHost`, Hilt root, API keys via `BuildConfig` / manifest placeholders.
- **`:core:common`** — `DispatcherProvider` (IO / Main).
- **`:core:model`** — Domain models, `JourneyRepository` / `TrackingRepository` interfaces.
- **`:core:network`** — Retrofit `TflApi`, DTOs, `NetworkModule`, HTTP error mapping.
- **`:core:data`** — Repository implementations, mappers, virtual GPS join, `DataModule`.
- **`:core:designsystem`** — Material 3 colours, typography, `Theme.kt`.
- **`:feature:journey`** — Journey planning screen + `JourneyViewModel`.
- **`:feature:tracking`** — Live map + telematics screen + `TrackingViewModel`.

---

## Libraries & dependencies

Versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Below is the **complete** third-party dependency set (plus which module declares each).

### Build tooling (plugins)

| Plugin | Version |
|--------|---------|
| Android Gradle Plugin | 8.9.2 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Hilt Gradle Plugin | 2.56.2 |
| Kotlin Compose Compiler | 2.0.21 |
| Kotlin Serialization | 2.0.21 |

### Runtime libraries (by category)

| Library | Version | Used in |
|---------|---------|---------|
| **AndroidX Core KTX** | 1.18.0 | `:app`, `:feature:journey`, `:feature:tracking` |
| **Activity Compose** | 1.13.0 | `:app` |
| **Lifecycle Runtime KTX** | 2.10.0 | `:app`, `:feature:journey`, `:feature:tracking` |
| **Lifecycle Runtime Compose** | 2.10.0 | `:feature:journey`, `:feature:tracking` |
| **Lifecycle ViewModel KTX** | 2.10.0 | `:feature:journey`, `:feature:tracking` |
| **Lifecycle ViewModel Compose** | 2.10.0 | `:feature:journey`, `:feature:tracking` |
| **Navigation Compose** | 2.9.0 | `:app`, `:feature:journey`, `:feature:tracking` |
| **Compose BOM** | 2024.09.00 | `:app`, `:core:designsystem`, `:feature:journey`, `:feature:tracking` |
| **Compose UI** | (BOM) | `:app`, `:core:designsystem`, `:feature:journey`, `:feature:tracking` |
| **Compose UI Graphics** | (BOM) | `:core:designsystem` |
| **Compose Material 3** | (BOM) | `:app`, `:core:designsystem`, `:feature:journey`, `:feature:tracking` |
| **Compose Material Icons Extended** | (BOM) | `:feature:tracking` |
| **Compose UI Tooling** | (BOM) | `:app`, `:core:designsystem` (debug) |
| **Compose UI Tooling Preview** | (BOM) | `:core:designsystem` |
| **Hilt Android** | 2.56.2 | `:app`, `:core:network`, `:core:data`, `:feature:journey`, `:feature:tracking` |
| **Hilt Compiler (KSP)** | 2.56.2 | same as Hilt (ksp) |
| **Hilt Navigation Compose** | 1.2.0 | `:feature:journey`, `:feature:tracking` |
| **Kotlinx Coroutines Core** | 1.10.2 | `:core:common`, `:core:model` |
| **Kotlinx Coroutines Android** | 1.10.2 | `:core:network`, `:core:data` |
| **Kotlinx Serialization JSON** | 1.8.0 | `:core:network` |
| **Retrofit** | 2.11.0 | `:core:network` |
| **Retrofit Kotlinx Serialization Converter** | 1.0.0 | `:core:network` |
| **OkHttp** | 4.12.0 | `:core:network` |
| **OkHttp Logging Interceptor** | 4.12.0 | `:core:network` |
| **Maps Compose** | 6.4.1 | `:feature:tracking` |
| **Play Services Maps** | 19.2.0 | `:feature:tracking` |

### Test / debug only

| Library | Version | Used in |
|---------|---------|---------|
| JUnit | 4.13.2 | `:app` (unit tests) |
| AndroidX JUnit | 1.3.0 | `:app` (instrumented) |
| Espresso Core | 3.7.0 | `:app` (instrumented) |
| Compose UI Test JUnit4 | (BOM) | `:app` (instrumented) |
| Compose UI Test Manifest | (BOM) | `:app` (debug) |

### External services (not Gradle artifacts)

| Service | Configuration |
|---------|----------------|
| TfL Unified API | `TFL_APP_KEY` in `local.properties` → `BuildConfig` |
| Google Maps SDK for Android | `MAPS_SDK_KEY` in `local.properties` → manifest `com.google.android.geo.API_KEY` |

---

## The virtual GPS problem

### Join algorithm

For each `PredictionDto` from `/Line/{lineId}/Arrivals`:

1. Require non-empty `vehicleId` and `naptanId`
2. Look up `naptanId` in a `Map<stopId, RouteStop>` built from route sequence
3. If found, create `VehiclePosition` at that stop’s `latitude` / `longitude`
4. Attach `timeToStation` for proximity UI (marker snippet, list text, progress bar)
5. Deduplicate by `vehicleId` (one marker per bus)

Route geometry is fetched **once per tracking session** and cached in the repository loop. Both **outbound** and **inbound** sequences are merged so buses running in either direction can be matched.

### Disambiguation

**Location (before journey request):**

- `StopPoint/Search` → 0 / 1 / N matches
- 1 match: auto-resolve to stop `id`
- N matches: dialog; user selection stores resolved `id`
- Type-ahead suggestions (debounced) reuse the same search API

**Journey (from API response):**

- `fromLocationDisambiguation` / `toLocationDisambiguation` in `JourneyResults`
- Mapped to `DisambiguationOption(parameterValue, displayName)`
- Re-request journey with selected `parameterValue`

---

## Setup

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 11+
- Android SDK 36
- Device or emulator with **Google Play services** (required for Maps SDK)
- TfL API account and Google Cloud project (see below)

### Run the app

1. Clone the repository.
2. Copy `local.properties.example` to `local.properties` in the project root.
3. Fill in `sdk.dir`, `TFL_APP_KEY`, and `MAPS_SDK_KEY` (see [API key configuration](#api-key-configuration)).
4. Open the project in Android Studio → **Sync Project with Gradle Files**.
5. Run the **`app`** configuration.

```bash
./gradlew :app:assembleDebug
```

### Verify a full path

1. **From:** `Victoria` · **To:** `Euston` → pick suggestions or disambiguate
2. **Find journeys** → tap a bus leg (e.g. route 15)
3. Tracking screen should show a map (route line + markers) and a list of active buses

If the map is blank, see [Maps SDK troubleshooting](#maps-sdk-troubleshooting).

---

## API key configuration

The app uses **two unrelated keys**. Do not mix them up.

| Key | Property | Consumed by | Purpose |
|-----|----------|-------------|---------|
| TfL Primary Key | `TFL_APP_KEY` | `BuildConfig` → Hilt `@TflAppKey` | All TfL REST calls (`?app_key=`) |
| Google Maps SDK | `MAPS_SDK_KEY` | Android manifest meta-data | Map rendering on tracking screen only |

`local.properties` is **gitignored**. Never commit real keys.

### Example `local.properties`

```properties
sdk.dir=/Users/you/Library/Android/sdk

# TfL Unified API
TFL_APP_KEY=your_tfl_primary_key_here

# Google Maps SDK for Android (NOT TfL)
MAPS_SDK_KEY=your_maps_sdk_key_here
```

Gradle reads these in `app/build.gradle.kts`:

- `TFL_APP_KEY` → `buildConfigField` (injected into repositories)
- `MAPS_SDK_KEY` → `manifestPlaceholders` → `com.google.android.geo.API_KEY` in `AndroidManifest.xml`

`MAPS_API_KEY` is supported as a legacy alias for `MAPS_SDK_KEY` only.

---

### TfL API key (`TFL_APP_KEY`)

1. Register at [api-portal.tfl.gov.uk](https://api-portal.tfl.gov.uk/).
2. **Products** → subscribe (e.g. *500 Requests per min*).
3. **Profile** → **Your Subscriptions** → **Show** next to **Primary Key**.
4. Paste into `TFL_APP_KEY`.

Append to every TfL request: `?app_key={TFL_APP_KEY}`

The legacy `app_id` + secondary key flow is **not** used. The secondary key is optional and unused in this project.

**If TfL calls fail:** empty key shows *Add TFL_APP_KEY to local.properties*; HTTP 429 shows a rate-limit message; 404 shows a not-found message.

---

### Google Maps SDK key (`MAPS_SDK_KEY`)

This key is **only** for embedding a map via **Maps SDK for Android**. It is **not**:

- The TfL `app_key`
- A “Maps API” REST key for Directions / Distance Matrix / Places (those are separate products)

**Setup:**

1. [Google Cloud Console](https://console.cloud.google.com/) → create or select a project.
2. **APIs & Services → Library** → enable **Maps SDK for Android** (not only “Maps JavaScript API” or “Directions API”).
3. **Credentials → Create credentials → API key**.
4. Paste into `MAPS_SDK_KEY`.
5. **Restrict the key (recommended):**
   - Application restriction: **Android apps**
   - Package name: `com.velaphi.journeytracker`
   - SHA-1: from Android Studio **Gradle → app → signingReport** or:

     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```

6. Enable **billing** on the Cloud project (Maps SDK requires it; free tier still applies).

The manifest entry (name is fixed by Google):

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_SDK_KEY}" />
```

#### Maps SDK troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| Grey map / “For development purposes only” | Billing not enabled or wrong API enabled |
| Map never loads | Missing `MAPS_SDK_KEY` or Gradle sync not run after editing `local.properties` |
| `Authorization failure` | SHA-1 / package restriction mismatch |
| Journey works, map doesn’t | TfL key is fine; fix Maps SDK key only |

After changing `local.properties`, run **Sync Gradle** and reinstall the app.

---

## Trade-offs

### Virtual GPS: stop-level position vs interpolation

| Choice | Rationale |
|--------|-----------|
| Place bus at **next stop coordinates** | Matches available data; simple, explainable join |
| No road interpolation | Assignment allows approximation; avoids fake precision |
| Merge inbound + outbound routes | Arrivals may reference stops on either direction |

**Cost:** Marker jumps stop-to-stop; does not show movement along roads.

### Architecture: MVVM vs MVI

| Choice | Rationale |
|--------|-----------|
| MVVM + `StateFlow` | Low boilerplate for three screens; easy to test repositories |
| Repositories own polling | Single source of truth for refresh interval; ViewModel stays thin |

**Cost:** No explicit intent/reducer model; complex navigation side-effects stay in ViewModel methods.

### Module split

| Choice | Rationale |
|--------|-----------|
| Feature modules per screen | Clear boundaries; aligns with team ownership |
| Shared `:core:data` | One place for TfL joins and caching rules |

**Cost:** More Gradle modules than a single-app prototype; acceptable for a lead-level sample.

### Polling vs WebSocket

| Choice | Rationale |
|--------|-----------|
| 30s HTTP polling | TfL assignment specifies fixed interval; API is REST-only |
| `WhileSubscribed` cancellation | Respects lifecycle without WorkManager complexity |

**Cost:** Up to 30s staleness; extra requests vs push (not offered by TfL for this use case).

### Static route cache

| Choice | Rationale |
|--------|-----------|
| Fetch route sequence once per session | Geometry rarely changes; reduces API load |
| Refetch arrivals every poll | Arrivals are the live signal |

**Cost:** Mid-session route changes (diversion) not reflected until screen is reopened.

### Bus-only journey filter

| Choice | Rationale |
|--------|-----------|
| Filter legs where `mode == bus` | Assignment scope: select a bus route to track |
| `mode=bus` on search and journey API | Reduces noise in results |

**Cost:** Multi-modal journeys appear as bus-only legs; tube/walk legs hidden.

### Error handling

| Choice | Rationale |
|--------|-----------|
| Map HTTP codes to short user messages | 404 / 429 / 5xx / network called out in UI |
| `Result` + `Flow<Result>` | Explicit failure path without exceptions in UI |

**Cost:** No retry/backoff UI; user must pull to refresh by leaving and re-entering screen.

### UI scope

| Choice | Rationale |
|--------|-----------|
| Material 3, functional layouts | Assignment de-prioritises pixel-perfect UI |
| Dialogs for disambiguation | Simple, accessible; no custom bottom-sheet framework |

**Cost:** Not aligned to high-fidelity Figma mocks (optional per brief).

### Secrets in `local.properties`

| Choice | Rationale |
|--------|-----------|
| Gradle → `BuildConfig` / manifest placeholders | Standard Android pattern; keys not in VCS |
| No remote config | Assignment: no auth / persistence required |

**Cost:** Keys in plain text on device; production would use backend proxy or Play App Signing + restricted keys.

---

## TfL endpoints used

| Feature | Method | Path |
|---------|--------|------|
| Location search | GET | `/StopPoint/Search/{query}?modes=bus&app_key=` |
| Journey planning | GET | `/Journey/JourneyResults/{from}/to/{to}?mode=bus&app_key=` |
| Live arrivals | GET | `/Line/{lineId}/Arrivals?app_key=` |
| Route geometry | GET | `/Line/{lineId}/Route/Sequence/outbound\|inbound?app_key=` |

Base URL: `https://api.tfl.gov.uk/` (see `NetworkConfig.BASE_URL`).

---

## Design questions (submission summary)

### Vehicle positioning

Infer position by joining `naptanId` from arrivals to route-sequence stop coordinates. `timeToStation` is a proximity hint only. Limitations: stop-level accuracy, missing matches dropped, no GPS ground truth.

### Disambiguation

Search ambiguity → user dialog or auto-single-match. Journey ambiguity → API `disambiguationOptions` → re-plan with `parameterValue`.

### Polling and UI state

Repository owns the poll loop; ViewModel exposes cold→hot `Flow` with `WhileSubscribed` so leaving the screen stops work.

### Team split (example)

- **Engineer A:** network + data + virtual GPS  
- **Engineer B:** journey feature + disambiguation  
- **Engineer C:** tracking feature + map + polling UX  

**Sprint 1:** TfL integration, journey screen, README. **Sprint 2:** tracking map, 30s poll, error/empty states.

### Server-side positioning (optional)

A backend could cache route geometry, run joins, and stream positions to many clients. Trade-off: infra cost and latency vs simpler, dumber mobile clients.

---

## Tech stack

| Area | Technology |
|------|------------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose, Material 3 |
| DI | Hilt |
| Navigation | Navigation Compose |
| Networking | Retrofit 2, OkHttp, kotlinx-serialization |
| Async | Coroutines, Flow |
| Maps | Maps SDK for Android, Maps Compose |
| Build | Gradle Kotlin DSL, KSP |

---

## License

Technical assessment / portfolio project.
