# Phase 10: CameraX Photo Capture & Attachment Pipeline

## Objective
Implement a fully native, offline-first image capture and photo attachment pipeline using Android CameraX. This feature enables enumerators to document water points (Wells, Springs, Dams) visually, downsamples and compresses images to manage disk space, associates files with the survey UUID, persists references in the database, and packages images in `.ywsync` files for offline synchronization.

## Current Project Context
The database schemas (`SurveyAttachmentEntity`, `SurveyRecordEntity`), DAOs (`SurveyAttachmentDao`), domain models, and `.ywsync` serialization/packaging logic are already fully implemented. However, the system currently has no way to capture photos or save files on the local filesystem. The camera permission is declared in `AndroidManifest.xml`, but the application does not make use of the camera hardware.

## Existing Files To Inspect
- **Build Configurations:** [`android_app/app/build.gradle.kts`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/build.gradle.kts) (to add CameraX dependencies)
- **Manifest:** [`android_app/app/src/main/AndroidManifest.xml`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/AndroidManifest.xml) (ensure permissions are correct)
- **Room Entity:** [`android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyAttachmentEntity.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyAttachmentEntity.kt)
- **Room DAO:** [`android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyAttachmentDao.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyAttachmentDao.kt)
- **Sync Packaging:** [`android_app/app/src/main/java/com/yemen/watersurvey/core/sync/SurveySyncExporter.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/sync/SurveySyncExporter.kt) (inspect how attachments are compressed into `.ywsync`)
- **Survey Screens:** [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/NewWellSurveyScreen.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/NewWellSurveyScreen.kt) (similar for Spring and Dam screens)

## Requirements
1. **CameraX Integration:** Equip the application with CameraX dependencies (core, camera2, lifecycle, view).
2. **Camera Preview & Capture UI:** Create a Compose-compatible camera preview component and custom overlay with a capture button.
3. **Strict Downsampling & JPEG Compression:**
   - Image resolution must not exceed **1920x1080** (Full HD).
   - Images must be compressed to JPEG format with a quality setting of approximately 80%.
   - Target final file size must be **approximately 300KB** (maximum limit of 500KB) to ensure compatibility with offline device-to-device transfers.
4. **Survey UUID Association:** Map each captured photo to the active survey's `surveyUUID`.
5. **Persistence (`SurveyAttachmentEntity`):** Record attachment metadata in Room:
   - Unique `attachmentId`
   - Target `surveyUUID`
   - Reference `recordId`
   - Local path (e.g. `attachments/ATT_<uuid>.jpg` under internal files directory)
   - Calculated SHA-256 checksum
   - File size in bytes
   - Timestamp of capture
6. **Sync Compatibility:** Ensure the captured images are saved inside the internal files directory matching the directory structure expected by `SurveySyncExporter` (`attachments/` folder).
7. **Physical Device Verification:** The camera preview, capture, compression, and UI flows must be tested and validated to function smoothly on a physical Android device.

## Implementation Steps
1. **Dependency Ingestion:** Add CameraX dependencies in `build.gradle.kts` and sync the project.
2. **Camera Manager / Helper Class:** Create a camera capture helper that sets up `ImageCapture` and handles image resizing, rotation correction, downsampling, and JPEG compression.
3. **Capture UI Screen:** Build a camera capture screen / composable with a preview window, a capture button, and a visual feedback overlay.
4. **Wired Survey Integration:**
   - In `NewWellSurveyScreen`, `NewSpringSurveyScreen`, and `NewDamSurveyScreen`, add an "Add Photo" button.
   - Display a preview thumbnail of the captured photo.
   - Wire the photo path into the local ViewModel state.
5. **Database Transaction:** In `SurveyViewModel.saveSurvey()`, save the corresponding `SurveyAttachmentEntity` records in Room alongside the survey record.
6. **Testing & Verification:** Verify files are written locally, and export them into a `.ywsync` file to confirm packaging and checksum validations succeed.

## Constraints / Do Not Change
- **Do NOT bypass or change database schema versions.** Avoid breaking Room migrations; use existing database schemas.
- **Do NOT use external storage permissions (Scoped Storage)** unless standard internal app directories (`context.filesDir`) are used. internal sandboxed storage is preferred.
- **Do NOT change the `.ywsync` format structure.** Files must be stored in the expected `attachments/` folder in the package.

## Testing Requirements
- **Unit Tests:** Write JUnit tests verifying image compression output sizes and SHA-256 hash generation for mock file inputs.
- **Integration Tests:** Verify that saving a survey with an attachment writes the entity to SQLite and that the entry is readable via `SurveyAttachmentDao`.
- **Physical/Emulator Verification:** Verify that runtime camera permissions are requested correctly, preview renders without black screen, and capture completes without crashes.

## Final Report Requirements
Compile a `WALKTHROUGH.md` or update the changelog including:
- Screenshots of the Camera UI and captured thumbnails inside survey forms.
- File size stats for test photos demonstrating compression gate compliance (< 300KB).
- Logcat outputs showing successful database insert and sync packaging.
