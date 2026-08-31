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
   - Target image size:
     - Preferred range: 200KB–500KB
     - Hard maximum: 500KB
     - Preserve engineering evidence quality while optimizing offline transfer.
4. **Database Schema Safety:** Before modifying `SurveyAttachmentEntity` or Room schema, inspect the existing fields. Do not introduce Room migrations unless absolutely required. Reuse existing schema whenever possible.
5. **Survey UUID Association:** Map each captured photo to the active survey's `surveyUUID`.
6. **Persistence (`SurveyAttachmentEntity`):** Record attachment metadata in Room:
   - Unique `attachmentId`
   - Target `surveyUUID`
   - Reference `recordId`
   - Local path (e.g. `attachments/ATT_<uuid>.jpg` under internal files directory)
   - Calculated SHA-256 checksum
   - File size in bytes
   - Timestamp of capture
7. **Sync Compatibility:** Ensure the captured images are saved inside the internal files directory matching the directory structure expected by `SurveySyncExporter` (`attachments/` folder).
8. **Physical Device Verification:** The camera preview, capture, compression, and UI flows must be tested and validated to function smoothly on a physical Android device.

## Implementation Steps
1. **Dependency Ingestion:** Add CameraX dependencies in `build.gradle.kts` and sync the project.
2. **Camera Manager / Helper Class:** Create a camera capture helper that sets up `ImageCapture` and handles image resizing, rotation correction, downsampling, and JPEG compression.
3. **Capture UI Screen:** Build a camera capture screen / composable with a preview window, a capture button, and a visual feedback overlay.
4. **Wired Survey Integration:**
   - In `NewWellSurveyScreen`, `NewSpringSurveyScreen`, and `NewDamSurveyScreen`, add an "Add Photo" button.
   - Display a preview thumbnail of the captured photo.
   - Wire the photo path into the local ViewModel state.
5. **Database Transaction:** Use the existing attachment data layer or create an AttachmentRepository if required. SurveyViewModel should coordinate the save operation but must not contain file handling logic, image compression logic, or direct attachment persistence logic.
6. **Testing & Verification:** Verify files are written locally, and export them into a `.ywsync` file to confirm packaging and checksum validations succeed.

## Constraints / Do Not Change
- **Do NOT bypass or change database schema versions.** Avoid breaking Room migrations; use existing database schemas.
- **Do NOT use external storage permissions (Scoped Storage)** unless standard internal app directories (`context.filesDir`) are used. internal sandboxed storage is preferred.
- **Do NOT change the `.ywsync` format structure.** Files must be stored in the expected `attachments/` folder in the package.
- **Phase Isolation Rule:** Camera implementation must not modify survey workflow status logic. Do not implement DRAFT lifecycle changes in this phase. Draft editing belongs exclusively to Phase 11.

## Testing Requirements
- **Unit Tests:** Write JUnit tests verifying image compression output sizes and SHA-256 hash generation for mock file inputs.
- **Integration Tests:** Verify that saving a survey with an attachment writes the entity to SQLite and that the entry is readable via `SurveyAttachmentDao`.
- **Physical/Emulator Verification:** Verify that runtime camera permissions are requested correctly, preview renders without black screen, and capture completes without crashes.

## Final Report Requirements
Compile a `WALKTHROUGH.md` or update the changelog including:
- Document UI verification results. Attach screenshots only if available.
- File size stats for test photos demonstrating compression gate compliance (200KB–500KB range, max 500KB).
- Logcat outputs showing successful database insert and sync packaging.
