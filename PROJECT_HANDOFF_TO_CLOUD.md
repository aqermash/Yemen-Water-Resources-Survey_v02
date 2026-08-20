# PROJECT_HANDOFF_TO_CLOUD.md
# Yemen Water Survey Android Application — Safe Handoff State

## Purpose

This document is a compact handoff for a new coding agent (Cloud/Claude/etc.) to continue the project **without restarting work, undoing valid changes, or spending tokens rediscovering project history**.

The agent must treat the current working tree as the source of truth and follow the sequence below.

---

# 1. Current Git State

Last committed baseline:

```text
479c697 — Initial project baseline
```

The working tree currently contains **12 modified files**. These changes are NOT committed yet.

Modified files:

```text
PROJECT_STATE.md
android_app/app/build.gradle.kts
android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageManager.kt
android_app/app/src/main/java/com/yemen/watersurvey/core/identity/EnumeratorProfileManager.kt
android_app/app/src/main/java/com/yemen/watersurvey/core/sync/SupervisorSyncWorkspaceManager.kt
android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt
android_app/app/src/main/java/com/yemen/watersurvey/domain/model/SurveyModels.kt
android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/ExportScreen.kt
android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyAdminLocationBindingSection.kt
android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyMergeReviewScreen.kt
android_app/app/src/test/java/com/yemen/watersurvey/core/admin/AdminReferenceTest.kt
android_app/gradle.properties
```

**Do not reset, restore, discard, or overwrite these changes.**

Do not commit them until the required tests are genuinely green.

---

# 2. What the Previous Agent Was Doing

The project was in:

```text
P2 — Compilation / Robolectric / KAPT / Room test repair
```

The previous agent was instructed to:

1. Inspect the existing project.
2. Repair only the current compilation/test problems.
3. Avoid broad refactoring.
4. Avoid touching the application entry point.
5. Run:

```text
./gradlew test
```

and obtain a real:

```text
BUILD SUCCESSFUL
```

6. Then run:

```text
./gradlew assembleDebug
```

and obtain a real:

```text
BUILD SUCCESSFUL
```

7. Only after both succeed, create an independent Git commit containing only the P2 fixes.
8. Update `PROJECT_STATE.md`.
9. Stop.

The previous coding agent stopped because its tool/account quota was exhausted. The modified files remain on disk.

---

# 3. Important: Do NOT Start P2.5 Yet

A separate read-only runtime audit discovered a pre-existing launch blocker:

```text
AndroidManifest.xml declares:
android:name=".MainActivity"
```

but `MainActivity` is absent from the source tree.

A real-device launch produced:

```text
FATAL EXCEPTION: main
RuntimeException: Unable to instantiate activity
Caused by:
ClassNotFoundException:
com.yemen.watersurvey.MainActivity
```

This is a genuine runtime finding.

However:

**Do NOT fix MainActivity in the current task.**

This is a separate task:

```text
P2.5 — Entry Point Implementation
```

It starts only after P2 reaches a clean committed state.

---

# 4. P2.5 Findings — Record Only, Do Not Implement Yet

The runtime audit found:

- `MainActivity` missing.
- No custom `Application` class.
- No `NavHost` wiring.
- No actual `@Composable` theme wrapper in `Theme.kt`.
- Several `ScreenRoute` values have no implemented destination:
  - Dashboard
  - SurveyForms
  - RecordsManager
  - Settings

The crash happens before Room, DataStore, FormPackageManager, or Compose business logic is reached.

This is a **pre-existing entry-point gap**, not a regression caused by the current P2 changes.

Do not mix these fixes into the current P2 commit.

---

# 5. Critical Administrative Data Rule

The latest authoritative administrative source is a **JSON file that includes villages**.

The hierarchy is:

```text
Governorate
    ↓
District
    ↓
Uzlah
    ↓
Village
```

The administrative reference is authoritative and must not be replaced by survey-form names.

Important distinction:

### Administrative reference

Contains official location identity:

```text
admin1 P-code
admin2 P-code
admin3 P-code
village-level identifier
official Arabic names
parent-child relationships
```

### Survey form data

May contain fields such as:

```text
site name
local site name
well name
spring name
dam/barrier name
other local descriptions
```

These are **survey attributes**, not administrative identifiers.

---

# 6. Do NOT Invent admin4Pcode

Do NOT add:

```text
admin4Pcode
```

just because villages are now present.

The actual village identifier field must be taken from the latest authoritative JSON schema.

Possible historical names in the project include:

```text
villageCode
villageReferenceId
```

Do NOT guess between them.

**Inspect the actual latest JSON schema first and preserve its authoritative identifier.**

The desired logical model is:

```text
admin1Pcode
admin2Pcode
admin3Pcode
village-level identifier from authoritative JSON
```

with corresponding Arabic name snapshots.

---

# 7. Critical Warning About Current P2 Changes

The current working tree contains this type of change:

```kotlin
val admin1Pcode: String = "YE11"
val admin2Pcode: String = "YE1101"
val admin3Pcode: String = "YE110101"
```

in `SurveyRecordEntity` and `SurveyRecord`.

These values appear to have been introduced to satisfy constructor/test compilation.

They must NOT become the final business behavior.

Administrative P-codes for a real survey must come from the selected administrative assignment/reference data.

Do not allow a newly created survey to silently acquire fixed Yemen administrative defaults.

Before committing P2, determine whether these defaults are genuinely required by tests or whether tests/models should be corrected in a minimal, architecture-safe way.

Do not perform broad redesign.

---

# 8. Current Architectural Principle

A survey record must preserve the administrative selection made during survey collection.

Conceptually:

```text
Administrative JSON
        ↓
Room reference tables
        ↓
Assigned areas filter
        ↓
Enumerator selects:
Governorate → District → Uzlah → Village
        ↓
SurveyRecord stores administrative identifiers
        ↓
SurveyRecord stores Arabic name snapshots
```

The survey's local/site name is separate from this administrative identity.

---

# 9. Required Immediate Workflow

The next coding agent must work in this exact order.

## Step A — Inspect, do not modify immediately

Run:

```powershell
git status
git diff --stat
git diff
```

Then inspect:

```text
PROJECT_STATE.md
README.md
android_app/app/build.gradle.kts
android_app/gradle.properties
```

Also inspect only the relevant files changed in the current working tree.

Do not redo the whole project analysis unless evidence requires it.

---

## Step B — Run the actual current test state

From:

```text
D:\Dev\Project Yemen Water Survey_v02ndroid_app
```

run:

```powershell
.\gradlew.bat test
```

Capture the real current errors.

Do not assume the old KAPT/Robolectric error is still the current error.

---

## Step C — Fix only P2 problems

Current scope:

```text
Robolectric
KAPT
Room annotation processing
test compilation
test-specific API mismatches
```

Use minimum safe changes.

Do not:

- add MainActivity
- add Application
- redesign navigation
- redesign forms
- add flavors
- redesign database
- add networking
- change administrative authority
- invent admin4Pcode
- weaken or delete tests
- bypass compiler errors blindly
- perform broad refactoring

---

## Step D — Gate 1

Run:

```powershell
.\gradlew.bat test
```

Required result:

```text
BUILD SUCCESSFUL
```

A partial build, skipped tests, or compilation-only success is NOT sufficient.

---

## Step E — Gate 2

Only after Gate 1 succeeds:

```powershell
.\gradlew.bat assembleDebug
```

Required:

```text
BUILD SUCCESSFUL
```

---

## Step F — Commit

Only after both gates are green:

Create a Git commit containing only the P2 fixes.

Before committing:

```powershell
git diff --check
git status
```

Do not include unrelated P2.5 work.

---

## Step G — Update PROJECT_STATE.md

Record:

- new commit hash
- P2 completed
- exact test command and result
- exact assemble command and result
- remaining P2.5 entry-point blocker

Then stop.

---

# 10. Next Separate Task: P2.5

After P2 is committed, begin a separate task:

```text
P2.5 — Entry Point Implementation
```

Scope:

```text
MainActivity
Application class only if actually needed
Material 3 Compose theme wrapper
NavHost
Enumerator entry point
Supervisor entry point
missing route destinations
flavor-specific startup
```

Then perform real device/emulator launch verification.

The previous runtime evidence must be retained:

```text
ClassNotFoundException:
com.yemen.watersurvey.MainActivity
```

Do not claim runtime verification until an APK has actually been installed and launched successfully.

---

# 11. Project Architecture — Non-Negotiable Requirements

The application is intended to be:

- Native Android
- Kotlin
- Jetpack Compose + Material 3
- Arabic-first RTL
- Clean Architecture + MVVM
- Room/SQLite
- 100% offline-first
- No Firebase
- No cloud backend
- No networking dependency
- Manual file-based transfer
- Dynamic JSON/XLSForm-derived forms
- PDF output
- OOXML Excel output
- `.ywsync` device-to-device transfer
- Supervisor conflict detection and merge
- Administrative assignment filtering
- Village-level administrative reference

Do not introduce cloud/network infrastructure.

---

# 12. Two-App Direction

The final architecture is intended to produce two Gradle flavors/apps:

```text
enumerator
    → field survey collection

supervisor
    → PIN-protected consolidation/review/sync
```

But this is **future work after P2**.

Do not implement the flavor/entry-point work during the current P2 repair unless explicitly instructed.

---

# 13. Token-Efficiency Rule for the Coding Agent

To minimize token usage:

1. Do not repeat this handoff back to the user.
2. Do not rewrite the entire architecture unless necessary.
3. Inspect only files relevant to the current error.
4. Run the build/test command before theorizing.
5. Fix one verified root cause at a time.
6. After each meaningful fix, rerun the narrowest relevant test.
7. Use full `./gradlew test` as the final gate.
8. Do not explore unrelated screens or features during P2.
9. Do not implement P2.5 early.
10. Do not ask for information already contained in this document.

---

# 14. Definition of Done for Current Task

P2 is NOT complete until all of these are true:

```text
[ ] Current working-tree changes inspected
[ ] Current test failure reproduced
[ ] P2 root causes fixed minimally
[ ] ./gradlew test → BUILD SUCCESSFUL
[ ] ./gradlew assembleDebug → BUILD SUCCESSFUL
[ ] git diff --check clean
[ ] P2-only Git commit created
[ ] PROJECT_STATE.md updated with commit/evidence
[ ] Stop
```

The missing `MainActivity` remains intentionally deferred to:

```text
P2.5 — Entry Point Implementation
```

---

# 15. Final Handoff Summary

Current state:

```text
Last committed baseline:
479c697

Current branch:
master

Working tree:
12 modified files

Current phase:
P2 — Compile/Test repair

P2:
IN PROGRESS

MainActivity:
MISSING — confirmed by real-device ClassNotFoundException

P2.5:
NOT STARTED

Administrative hierarchy:
Governorate → District → Uzlah → Village

Administrative source:
Latest authoritative JSON including villages

admin4Pcode:
DO NOT INVENT

Village identifier:
Must match the actual latest JSON schema

Current fixed P-code defaults:
DO NOT accept as final business behavior without verification

Immediate next action:
Run ./gradlew.bat test and continue P2 only.
