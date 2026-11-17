# RepTrack architecture index

## Scope and freshness

- Repository: `EngineerASU-code/RepTrack`; default branch and checkout branch: `main`.
- Graphify Cloud: `READY`, queryable; indexed commit and local `HEAD` both equal `648404812444da2eaa87e7608f94eff1856396bd`.
- Snapshot: 2026-11-17, indexed at `2026-11-17T07:05:33Z`. Graph summary: 683 nodes, 2,342 edges, 22 communities.
- Code-context capability is available; pattern queries are unavailable. This map reflects the indexed commit, not untracked workspace files. The checkout has local-only materials outside that snapshot.

## Main modules

| Area | Entry and responsibility | Key connections |
| --- | --- | --- |
| Android offline app | `android/app/src/main/java/app/reptrack/MainActivity.kt`: `MainActivity.onCreate()` starts the Compose UI; `RepTrackApp()` owns tab and screen routing. | Screens collect `DiaryViewModel` state and call its actions. `DiaryViewModel` exposes snapshots and forwards mutations to `DiaryRepository`. |
| Android data and domain | `android/app/src/main/java/app/reptrack/DiaryRepository.kt` contains diary models and repository operations; storage is versioned SQLite via `SQLiteOpenHelper`. | `DiaryLogic.kt` and `DiaryAnalytics.kt` hold calculations; `VisualProgress.kt` renders progress from repository-backed state. `DiaryArchive.kt` handles backup/import, while `SecureExchange.kt` and `TelegramDelivery.kt` handle protected exchange and delivery files. |
| Telegram relay | `telegram-bot/src/index.mjs`: Cloudflare Worker `fetch()` entry handles Telegram webhook requests, route registration, and delivery flows. | `telegram-bot/migrations/0001_init.sql` defines D1 tables; `telegram-bot/test/index.test.mjs` covers update and parsing behavior. This is an optional Stage 3 relay; the Android diary and local Stage 2 do not depend on it. Deployment requires owner-managed Cloudflare and Telegram configuration. |
| Browser prototype | `web/index.html` is the prototype; `web/server.mjs` serves it locally. | `dist/index.html` is the documented static-hosting copy. This path is separate from Android packaging. |

## Tests and evidence

- Android unit coverage linked by Graphify includes `android/app/src/test/java/app/reptrack/TrainingSessionTest.kt` and the synthetic demo import test `android/app/src/test/java/app/reptrack/DemoWorkoutReportsTest.kt`.
- Android instrumented suites in the graph include repository/migration, accessibility, visual acceptance, workout keyboard, muscle-map, and synthetic preview-seed coverage. Relevant files include `DiaryRepositoryInstrumentedTest.kt`, `AccessibilityAdaptiveTest.kt`, `VisualAcceptanceTest.kt`, `VisualAcceptanceSupplementTest.kt`, `WorkoutImeAcceptanceTest.kt`, and `MuscleMapMappingTest.kt`.
- The debug-only demo import is covered by `DemoWorkoutReportsTest.kt` and `DemoReportsInstrumentedTest.kt`; its fixture contains synthetic records only.
- Telegram Worker tests are in `telegram-bot/test/index.test.mjs`.
- Graph-derived test links are incomplete: `graphify_tests_for` found direct links from repository and Telegram code, but no direct test link from `MainActivity.kt`. Treat an empty result as missing graph coverage, not proof that runtime tests do not exist.
- UI flow coverage is explicit in source even when Graphify has no direct edge: `WorkoutImeAcceptanceTest.kt`, `VisualAcceptanceTest.kt`, `VisualAcceptanceSupplementTest.kt`, `ThemeVisualAcceptanceTest.kt`, and `AccessibilityAdaptiveTest.kt` exercise Compose flows launched from `MainActivity.kt`.

## Confidence and limits

- **High:** Android UI → ViewModel → repository structure, SQLite-backed data boundary, archive/exchange files, and Telegram Worker entry point; supported by fresh Graphify results and repository READMEs.
- **Medium:** Browser prototype and static-copy relationship; documented in the root README and confirmed by tracked paths, but not surfaced in Graphify's ranked code results.
- Graphify is a structural aid, not deployment or runtime verification. The map does not claim that Cloudflare services are deployed. Untracked workspace assets and tools are outside the indexed commit.
