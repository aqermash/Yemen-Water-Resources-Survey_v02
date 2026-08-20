# PROJECT_STATE.md

## 1. Project identity fingerprint
- Project name: Yemen Water Survey — `AiStudioApp`
- Package: `com.yemen.watersurvey`
- Stack: Kotlin, Jetpack Compose + Material 3, Room DB (current version, e.g. v6), Coroutines, native OOXML Excel export, native PDF export, `.ywsync` offline sync protocol
- Key files: `SurveyAppDatabase.kt`, `SurveyRecordEntity.kt`, `RegistryCodeGenerator.kt`, `EnumeratorProfileManager.kt`, `FormPackageManager.kt`, `ConflictDetectionEngine.kt`

## 2. Locked decisions log
- `registryCode` format: `YE<admin1><admin2><admin3>-<type>-<sequence>`
- Facility type codes: `WL` = well, `SP` = spring, `WH` = harvesting structure/dam — **locked, do not change**
- Enumerator identity: single `enumeratorCode` (no manual name entry), reused as `AuditLogEntity.actorId` and `SyncPackageEntity.senderUsername`, stamped on `SurveyRecordEntity.enumeratorCode`
- Sequence pool bootstrap: initial ranges shipped inside the form package (`sequence_pool.json`) at provisioning time; `PENDING` fallback when exhausted offline

## 3. Phase/milestone tracker
| Phase | Status | Evidence/Notes |
|-------|--------|----------------|
| P0    | done   | Project initialization |
| P1    | done   | |
| P2    | done   | Compilation/KAPT/Robolectric/Room fixes. `./gradlew test` (46/46 passed) and `./gradlew assembleDebug` verified green. |
| P2.5  | not started | **Entry Point Implementation** — see Locked Finding below; sequenced **after** P2 reaches clean committed state. |

### Locked Finding — Missing Application Entry Point (P2.5)
- **Finding:** `MainActivity` is declared in `AndroidManifest.xml` (`android:name=".MainActivity"`) but does not exist anywhere in the source tree. Confirmed via runtime `ClassNotFoundException: com.yemen.watersurvey.MainActivity` captured via ADB on cold launch (2026-08-18) — the crash log is sufficient evidence that the class is missing from the running APK. (Not confirmed via separate dex/APK inspection such as `apkanalyzer`; claim scoped to runtime ClassNotFoundException only.)
- **Scope:** No `Application` class, no `NavHost` wiring, no `Theme.kt` `@Composable` wrapper, and 4 of the routes in `ScreenRoute` (`Dashboard`, `SurveyForms`, `RecordsManager`, `Settings`) have no matching screen implementation.
- **Severity:** Blocking for any real-device use — independent of and unrelated to P1/P2 compile-error work, confirmed by git history back to the `479c697` baseline (pre-existing gap, not a P2 regression).
- **Status:** Not started. Tracked as P2.5 — sequenced after P2 compile/test fix task reaches a clean, committed state.

## 4. Binding Rules

### 4.1 Git Commit Rule
- **Commit immediately after any full clean build + test run that is confirmed green**, before starting the next task. Never let verified-good, uncommitted work sit at risk of being lost to an interruption.
- **Do not commit** on the basis of a claimed/summarized completion — only after the actual build/test command output has been seen and confirmed passing.
- If work is interrupted mid-task with uncommitted changes, the next session must treat those uncommitted changes as **unverified** — re-run a full build to check their real state before trusting or continuing them, and only commit once re-verified.
- Commit messages should reference the phase/item from the phase tracker (e.g. `P2: fix DeviceSequenceDao unresolved reference, full build+test green`) so `git log` alone gives a readable history of verified milestones, cross-referenced with the phase tracker in `PROJECT_STATE.md`.

### 4.2 Mandatory Session-Start Sequence
Every new session (new account, new tool, resumed after any interruption) must, before doing anything else:
1. Read `PROJECT_STATE.md`'s "Last Confirmed Checkpoint" section.
2. Run `git status` and `git log -1` and compare against it.
3. If `git status` shows uncommitted changes, or `git log -1` doesn't match the recorded checkpoint hash, explicitly report the discrepancy before proceeding — don't silently assume either the file or the working tree is correct.

## 5. Last Confirmed Checkpoint
- **Commit hash:** `6b6e644ab2cd48f1411a63a8289716566cc6a7bb`
- **Date/Session:** `2026-08-20 22:37:47 +0300`
- **Verified state:** VERIFIED GREEN — `./gradlew test` BUILD SUCCESSFUL (46/46 unit tests passed), `./gradlew assembleDebug` BUILD SUCCESSFUL
- **Build & Test Verification (2026-08-20):**
  - `.\gradlew.bat test` -> BUILD SUCCESSFUL (62 actionable tasks: 1 executed, 61 up-to-date, all 46 tests pass)
  - `.\gradlew.bat assembleDebug` -> BUILD SUCCESSFUL (36 actionable tasks: 1 executed, 35 up-to-date)

**Next action:** Commit P2 changes cleanly, then proceed to P2.5 (Entry Point Implementation).

## 6. Change log
- 2026-08-16: Created mandatory PROJECT_STATE.md continuity protocol, logged fixed project identity fingerprint to ensure context locks on AiStudioApp, com.yemen.watersurvey.
- 2026-08-17: Investigated "Unresolved reference: Amber200" error at SurveySyncImportScreen.kt:599.

  **Root cause:** Amber200 WAS properly defined in Theme.kt (line 37: `val Amber200 = Color(0xFFFDE68A)`) and the wildcard import `import com.yemen.watersurvey.presentation.theme.*` WAS present. The error was stale Kotlin daemon cache.

  **Fix applied:** No code change needed for Amber200 itself. Added missing color definitions to Theme.kt to prevent cascade errors: Amber600-900, Emerald600, Rose600-900, Red400-950.

  **Build status (2026-08-17):** Amber200 error RESOLVED. However, build still fails due to OTHER pre-existing errors:
  - `Unresolved reference: inspectPackage` (multiple files)
  - `Unresolved reference: DeviceSequenceDao`
  - Missing type `SelectionConsistencyResult`
  - Missing methods `getDistricts/getSubDistricts/getVillages`
  - Missing enum constants: `NO_GPS_FIX`, `LOCATION_MATCH`, `AMBIGUOUS_BOUNDARY`, `OUTSIDE_COVERAGE`
  - Missing Material Icons: `SyncAlt`, `Archive`, `FactCheck`, `Dashboard`
  - Type mismatches and wrong parameter names in SurveyAdminLocationBindingSection.kt

  **Full clean build NOT achieved** - these structural API mismatches must be addressed separately.
- 2026-08-18: Session started via **Cursor** (Trae hung/froze during previous task and had to be manually stopped). Scope: **diagnostic-only** — investigate `./gradlew test` failures (RobolectricTestRunner not found, Room KAPT `processingEnv must not be null`); no commit during this session.
-"راجع PROJECT_HANDOFF_TO_CLOUD.md للسياق الكامل قبل أي عمل"
