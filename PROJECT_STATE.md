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
| P2    | in progress | Working on corrections, running `gradlew test` |

## 4. Change log
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
