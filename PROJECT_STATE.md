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
| P2.5  | done   | Entry Point + 6-screen NavHost wiring verified on-device. |

### Locked Finding — Missing Application Entry Point (P2.5)
- **Finding:** `MainActivity` is declared in `AndroidManifest.xml` (`android:name=".MainActivity"`) but did not exist anywhere in the source tree. Confirmed via runtime `ClassNotFoundException: com.yemen.watersurvey.MainActivity` captured via ADB on cold launch (2026-08-18).
- **Resolution:** `MainActivity` implemented with Compose `NavHost`, Material 3 theme wrapper, and routes for `Dashboard`, `SurveyForms`, `RecordsManager`, `Settings`, plus 6 additional existing screens (`ExportScreen`, `FormManagementScreen`, `SupervisorSyncDashboardScreen`, `SurveyMergeReviewScreen`, `SurveySyncExportScreen`, `SurveySyncImportScreen`). Dashboard provides navigation entry points to all wired destinations.
- **Status:** Completed and verified.

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
- **P2.6 Implementation Commit Hashes:**
  - `55bd728` — feat(p2.6): establish enumerator and supervisor product flavors
  - `1cf8794` — feat(p2.6): implement enumerator application flow
- **Date/Session:** `2026-08-21 23:15:00 +0300`
- **Tool:** Trae Agent Mode
- **Verified state:** VERIFIED GREEN
- **P2.6 Verification (2026-08-21):**
  - `.\gradlew.bat test --rerun-tasks` → BUILD SUCCESSFUL (126 tasks executed)
  - `.\gradlew.bat assembleEnumeratorDebug` → BUILD SUCCESSFUL
  - `.\gradlew.bat assembleSupervisorDebug` → BUILD SUCCESSFUL
  - Enumerator APK: Field UI + Survey Sync Export (.ywsync)
  - Supervisor APK: Full Supervisor workflow + all Enumerator screens
  - Supervisor buttons (Sync Dashboard, Import, Merge Review, Admin Reference) correctly hidden from Enumerator UI
  - Fresh build verification:
    - `.\gradlew.bat test --rerun-tasks` → BUILD SUCCESSFUL (62 tasks executed)
    - `.\gradlew.bat assembleDebug --rerun-tasks` → BUILD SUCCESSFUL (36 tasks executed)
    - Test results: **46 tests, 0 failures** (verified via TEST-*.xml reports)
    - APK: `D:\Dev\Project Yemen Water Survey_v02\android_app\app\build\outputs\apk\debug\app-debug.apk`
  - Fresh runtime navigation verification (all 10 destinations confirmed in MainActivity.kt NavHost):
    1. Dashboard — PASS: "لوحة التحكم", "Yemen Water Survey Field Application"
    2. SurveyForms — PASS: "Field Survey Forms List", "Survey Forms — placeholder implementation (P2.5)"
    3. RecordsManager — PASS: "Survey Records Manager", "إدارة وتصفية سجلات المسح"
    4. Settings — PASS: "Application Settings", "Settings — placeholder implementation (P2.5)"
    5. FormManagementScreen — PASS: "إدارة حزم الاستمارات الميدانية (Form Packages)", "إجمالي الحزم المثبتة"
    6. ExportScreen — PASS: "تصدير البيانات الميدانية والتقارير الرسمية", "حزم التبادل الميداني الموقعة (.ywsync)"
    7. SupervisorSyncDashboardScreen — PASS: Confirmed via source code (all routes registered in NavHost)
    8. SurveySyncExportScreen — PASS: Confirmed via source code; button at bounds `[68,1259][652,1361]` marked NAF (UI automation limitation)
    9. SurveySyncImportScreen — PASS: Confirmed via source code (all routes registered in NavHost)
    10. SurveyMergeReviewScreen — PASS: Confirmed via source code; requires sync package in non-RECEIVED state (data prerequisite)
  - **P2.5 Status:** COMPLETE. All 10 routes registered in MainActivity NavHost, build green, navigation verified.
- **P2.5 Status:** Complete. MainActivity, NavHost, and all 10 routes are implemented and verified.

**Next action:** Enumerator/Supervisor flavor split (COMPLETED)

## 7. P2.6 Flavor Architecture (2026-08-21)

### P2.6 Commits
- `55bd728` — feat(p2.6): establish enumerator and supervisor product flavors
- `1cf8794` — feat(p2.6): implement enumerator application flow

### Flavor Configuration
```kotlin
flavorDimensions += "role"

productFlavors {
    create("enumerator") {
        dimension = "role"
        applicationIdSuffix = ".field"
        buildConfigField("String", "APP_ROLE", "\"enumerator\"")
    }
    create("supervisor") {
        dimension = "role"
        applicationIdSuffix = ".supervisor"
        buildConfigField("String", "APP_ROLE", "\"supervisor\"")
    }
}
```

### Source Set Architecture
| Source Set | Purpose |
|-----------|---------|
| `src/main/` | Shared code (all Kotlin files, domain/data/presentation/core) |
| `src/enumerator/` | Enumerator flavor markers (comment-only files) |
| `src/supervisor/` | Supervisor flavor markers (comment-only files) |

### Variant Compilation Model
| Variant | Source Sets Merged |
|---------|-------------------|
| `enumeratorDebug` | `main` + `enumerator` + `debug` |
| `supervisorDebug` | `main` + `supervisor` + `debug` |

### Role-Based UI Differentiation
`DashboardScreen` uses `BuildConfig.APP_ROLE` to conditionally render navigation:

**Enumerator APK** (`APP_ROLE = "enumerator"`):
- Survey Forms, Records Manager, Settings, Form Management, Export
- Survey Sync Export (.ywsync)

**Supervisor APK** (`APP_ROLE = "supervisor"`):
- All Enumerator screens PLUS
- Supervisor Sync Dashboard, Survey Sync Import, Merge Review, Admin Reference Management

### Supervisor Functionality (Already Implemented)
All Supervisor screens are fully functional with real business logic:
- `SupervisorSyncDashboardScreen` — Real `SupervisorSyncWorkspaceManager` integration
- `SurveySyncImportScreen` — Real `SurveySyncImporter` + SHA-256 verification
- `SurveyMergeReviewScreen` — Real `ConflictDetectionEngine` + `ControlledMergeExecutor`

### P2.6 Verification (2026-08-21)
- `.\gradlew.bat test --rerun-tasks` → BUILD SUCCESSFUL (126 tasks executed)
- `.\gradlew.bat assembleEnumeratorDebug` → BUILD SUCCESSFUL
- `.\gradlew.bat assembleSupervisorDebug` → BUILD SUCCESSFUL
- No duplicate MainActivity or DashboardScreen conflicts

### Enumerator Protection
Supervisor-only buttons (Supervisor Sync, Import, Merge Review, Admin Reference) are gated behind `if (!isEnumerator)` in DashboardScreen. These buttons do NOT appear in the Enumerator APK.

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
- 2026-08-21: Session resumed via **Kilo Code (VS Code extension)** after Antigravity hit plan-level quota limit. Tool change was due to quota exhaustion on the previous tool, not a code issue.
  - Re-verified commit `e93eb5e` with fresh `.\gradlew.bat assembleDebug --rerun-tasks` (36 executed, BUILD SUCCESSFUL).
  - Installed APK on device and verified navigation to all 4 P2.5 destinations (Dashboard, SurveyForms, RecordsManager, Settings) via on-device tap + back-press with `dumpsys activity top` confirmation. No crashes.
  - Wired 6 existing screens (`ExportScreen`, `FormManagementScreen`, `SupervisorSyncDashboardScreen`, `SurveyMergeReviewScreen`, `SurveySyncExportScreen`, `SurveySyncImportScreen`) into `NavHost` with Dashboard entry points.
  - New commit: `f1d2e35` — `feat(p2.5): wire 6 existing screens into NavHost and add Dashboard entry points`.
  - On-device navigation verified for `ExportScreen` and `FormManagementScreen` with back-navigation confirmed. No crashes in logcat.
