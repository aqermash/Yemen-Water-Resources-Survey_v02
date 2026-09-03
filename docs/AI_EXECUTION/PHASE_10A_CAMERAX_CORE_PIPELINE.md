# Phase 10A: CameraX Core Pipeline — Implementation Plan

**Phase:** 10A (of 10A → 10B → 10C)
**Scope:** CameraX dependency ingestion, `AttachmentRepository`, `PhotoCaptureManager`, unit tests
**Status:** IN PROGRESS
**Author:** Antigravity (Google DeepMind)
**Started:** 2026-08-31

---

## 1. Objective

Establish the core CameraX pipeline without any UI camera preview screen. Phase 10A is purely the **data + infrastructure layer**:

1. Add CameraX Gradle dependencies.
2. Implement `PhotoCaptureManager` — the single class responsible for disk I/O, image downsampling, JPEG compression, SHA-256 checksum, and file naming. It must NOT touch `SurveyViewModel`.
3. Implement `AttachmentRepository` — thin wrapper over `SurveyAttachmentDao` + `PhotoCaptureManager`. This is the only layer `SurveyViewModel` will eventually call.
4. Write unit tests for the compression gate and SHA-256 checksum logic.
5. Write a Room integration test verifying that an attachment entity can be inserted and retrieved via `SurveyAttachmentDao`.

Phase 10B (camera preview UI composable) and Phase 10C (survey screen wiring) are **out of scope** for this phase.

---

## 2. Architecture Decisions

### 2.1 Architecture Boundary (from Precheck Report Directive 2)

| Component | Responsibility |
|-----------|---------------|
| `PhotoCaptureManager` | File I/O, Bitmap downsampling, JPEG compression, SHA-256 |
| `AttachmentRepository` | Coordinates `PhotoCaptureManager` + `SurveyAttachmentDao`, returns `SurveyAttachmentEntity` |
| `SurveyViewModel` | Will later call `AttachmentRepository.savePhoto(...)` — no image logic inside |
| `SurveyAttachmentDao` | Existing — no changes required |
| `SurveyAttachmentEntity` | Existing — no changes required (zero migration) |

### 2.2 File Storage Convention

Images are saved under `context.filesDir/attachments/ATT_<uuid>.jpg`.

This matches the convention expected by `SurveySyncExporter.resolveAttachmentFile()` which probes:
1. Absolute path
2. `context.filesDir/<localFilePath>`
3. `context.cacheDir/<localFilePath>`

The `localFilePath` stored in `SurveyAttachmentEntity` will be the **relative path** `attachments/ATT_<uuid>.jpg` so `SurveySyncExporter` resolves it via strategy #2 (`context.filesDir/attachments/ATT_<uuid>.jpg`).

### 2.3 Image Compression Spec

| Constraint | Value |
|-----------|-------|
| Max resolution | 1920 × 1080 (Full HD, landscape-first, then portrait fallback 1080 × 1920) |
| JPEG quality | 80% |
| Target file size | 200 KB – 500 KB |
| Hard maximum | 500 KB |
| Fallback if >500 KB | Re-compress at quality=65, then 50 (two retries max) |

### 2.4 `attachmentType` value

For photos captured via the camera: `"PHOTO"`.

### 2.5 No Room Migration

`SurveyAttachmentEntity` already has all required columns:
- `attachmentId`, `surveyUUID`, `recordId`, `attachmentType`, `localFilePath`, `fileName`, `fileSizeBytes`, `fileSha256`, `capturedAt`, `sourcePackageId`

Zero schema changes. Room DB version remains at its current value.

---

## 3. New Files Created in Phase 10A

| File | Package | Purpose |
|------|---------|---------|
| `core/camera/PhotoCaptureManager.kt` | `com.yemen.watersurvey.core.camera` | Disk I/O, downsampling, compression, SHA-256 |
| `data/repository/AttachmentRepository.kt` | `com.yemen.watersurvey.data.repository` | Coordinates PhotoCaptureManager + DAO |
| `test/.../core/camera/PhotoCaptureManagerTest.kt` | `com.yemen.watersurvey.core.camera` | JUnit tests: compression gate, SHA-256 |
| `test/.../data/repository/AttachmentRepositoryTest.kt` | `com.yemen.watersurvey.data.repository` | Room integration test via Robolectric |

---

## 4. Modified Files in Phase 10A

| File | Change |
|------|--------|
| `android_app/app/build.gradle.kts` | Add CameraX dependencies (camera-core, camera2, camera-lifecycle, camera-view) |

---

## 5. Files NOT Modified in Phase 10A

- `SurveyAttachmentEntity.kt` — no change
- `SurveyAttachmentDao.kt` — no change
- `SurveyViewModel.kt` — no change (Phase 10C)
- `NewWellSurveyScreen.kt` — no change (Phase 10C)
- `NewSpringSurveyScreen.kt` — no change (Phase 10C)
- `NewDamSurveyScreen.kt` — no change (Phase 10C)
- `MainActivity.kt` — no change
- `ScreenRoute.kt` — no change (Phase 10B adds `CameraCapture` route)
- `AndroidManifest.xml` — no change (CAMERA permission already declared)
- `SurveySyncExporter.kt` — no change
- Room DB version — no change

---

## 6. CameraX Dependency Versions

```kotlin
// CameraX — Phase 10A
val cameraxVersion = "1.3.1"
implementation("androidx.camera:camera-core:$cameraxVersion")
implementation("androidx.camera:camera-camera2:$cameraxVersion")
implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
implementation("androidx.camera:camera-view:$cameraxVersion")
```

> CameraX 1.3.1 is stable, compatible with `compileSdk = 34` and `minSdk = 26`.

---

## 7. Unit Test Coverage Plan

### `PhotoCaptureManagerTest`

| Test | Input | Expected |
|------|-------|---------|
| `testCompressionProducesFileUnder500KB` | 2000×2000 synthetic Bitmap | Output JPEG ≤ 500 KB |
| `testCompressionProducesFileOver200KB` | 2000×2000 synthetic Bitmap with detail | Output JPEG ≥ 200 KB (smoke) |
| `testResolutionCapAt1920x1080` | 3000×2000 Bitmap | Saved image max dimension = 1920px |
| `testSha256IsConsistentForSameInput` | Same byte array × 2 | Both hashes identical |
| `testSha256DiffersForDifferentInputs` | Two different byte arrays | Hashes differ |
| `testFileNameFormatIsCorrect` | Any UUID | Filename matches `ATT_<uuid>.jpg` |

### `AttachmentRepositoryTest`

| Test | Description |
|------|-------------|
| `testSaveAttachmentEntityInsertedToRoom` | Mock compressed file → `insertAttachment()` → `getAttachmentsForSurvey()` returns 1 entry |
| `testAttachmentSha256MatchesFile` | Written file's SHA-256 must match entity's `fileSha256` field |

---

## 8. Completion Checklist

- [ ] `build.gradle.kts` updated with CameraX 1.3.1 dependencies
- [ ] `PhotoCaptureManager.kt` implemented
- [ ] `AttachmentRepository.kt` implemented
- [ ] `PhotoCaptureManagerTest.kt` written (6 tests)
- [ ] `AttachmentRepositoryTest.kt` written (2 tests)
- [ ] `./gradlew test` GREEN (all existing 46 + new 8 = 54 tests pass)
- [ ] `./gradlew assembleEnumeratorDebug` GREEN
- [ ] Git commit with message: `feat(p10a): CameraX core pipeline — PhotoCaptureManager, AttachmentRepository, 8 unit tests`
- [ ] `PROJECT_STATE.md` updated with Phase 10A entry

---

## 9. Phase 10B Preview (Out of Scope Here)

Phase 10B will add:
- `CameraCaptureScreen.kt` composable with CameraX `PreviewView` + capture button
- `ScreenRoute.CameraCapture` navigation route
- `MainActivity.kt` composable registration

Phase 10C will add:
- "Add Photo" button in `NewWellSurveyScreen`, `NewSpringSurveyScreen`, `NewDamSurveyScreen`
- Thumbnail preview in survey screens
- `SurveyViewModel.attachPendingPhoto(filePath)` coordination method
