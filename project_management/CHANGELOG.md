# Changelog: Yemen Water Survey Field Application

All notable changes to the **Yemen Water Survey Field Application** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.10.1] - 2026-08-15
### Added & Completed - PHASE 10.1: Production Reference Data Ingestion & Cross-Mapping Engine
- Created domain models in `AdminReferenceIngestionModels.kt` (`CrossMappingMethod`, `RawYemenInfoRecord`, `VillageCrossMappingResult`, `CrossMappingStatisticsReport`, `AdminIngestionValidationReport`, `ProductionReferencePackageContainer`).
- Implemented `AdminCrossMappingEngine.kt` providing a 6-stage cross-mapping pipeline, Arabic text normalization (Tashkeel stripping, character normalization, prefix removal), English transliteration normalization, geographic bounding evaluation, and strict enforcement of the Zero Fabricated P-Codes rule (`UNMAPPED` / `NEEDS_REVIEW` / `AMBIGUOUS`).
- Implemented `TopoJsonIngestionEngine.kt` for parsing and validating OCHA TopoJSON/GeoJSON structures into optimized `AdminGeometryEntity` records with bounding box pre-filtering ($O(1)$) and ring closure integrity checks.
- Implemented `AdminReferencePackageBuilder.kt` for assembling, validating, and installing production-grade offline administrative reference packages with SHA-256 integrity checksums.
- Enhanced `AdminReferencePackageManager.kt` with live cross-mapping report generation and batch ingestion capabilities.
- Expanded unit test suite `AdminReferenceTest.kt` with Arabic and English normalization, multi-stage cross-mapping verification, TopoJSON feature parsing, bounding-box checks, unmapped status rules, and override isolation tests.
- Updated documentation in `PROJECT_STATUS.md`, `DECISIONS_LOG.md` (Decision 015), `CHANGELOG.md`, and `NEXT_TASKS.md`.

---

## [2.10.0] - 2026-08-15
### Added & Completed - PHASE 10: Offline Administrative Reference & GIS Foundation
- Implemented core domain models in `AdminReferenceModels.kt` (`Admin1Governorate`, `Admin2District`, `Admin3Uzlah`, `AdminVillage`, `AdministrativeOverride`, `AdminPolygon`, `AdminBoundingBox`, `GpsAdminResolutionResult`, `AdminReferencePackageMetadata`, `AdminSource`, `MappingStatus`, `OverrideChangeType`).
- Implemented 2D Ray-Casting algorithm for offline Point-in-Polygon (PIP) testing in `AdminPolygon.containsPoint`.
- Upgraded Room database schema to v4 in `SurveyAppDatabase.kt` with `Admin1Entity`, `Admin2Entity`, `Admin3Entity`, `VillageEntity`, `AdminGeometryEntity`, `AdministrativeOverrideEntity`, `AdminReferencePackageEntity`, and their DAOs (`AdminReferenceDao`, `AdminGeometryDao`, `AdministrativeOverrideDao`, `AdminReferencePackageDao`).
- Implemented `AdminReferencePackageManager.kt` bootstrapping authoritative OCHA P-codes (22 Governorates, 333 Districts, 2,146 Uzlahs), Yemen-Info village enrichment (~41,494 records), TopoJSON geometry, SHA-256 package verification, and hierarchy validation.
- Implemented `GpsAdministrativeResolver.kt` providing offline GPS-to-Admin resolution (Gov -> Dist -> Uzlah -> Village), bounding-box candidate pre-filtering, and Haversine distance calculation.
- Implemented `AdministrativeOverrideManager.kt` allowing supervisors to record local/colloquial name overrides while keeping official OCHA records immutable and fully recoverable.
- Implemented `AdminCascadingSelector.kt` providing high-performance cascading selector queries, text search, and unmapped village inspection.
- Created `AdminReferenceManagementScreen.kt` featuring Material 3 Arabic RTL layout, version KPI metrics, hierarchy browser, override manager, unmapped village inspector, and GPS PIP tester.
- Added comprehensive unit test suite `AdminReferenceTest.kt` verifying PIP Ray-Casting, bounding-box pre-filtering, Haversine formula, village unmapped status preservation, and override immutability.
- Updated documentation in `PROJECT_STATUS.md`, `DECISIONS_LOG.md`, `CHANGELOG.md`, `NEXT_TASKS.md`, and `ARCHITECTURE.md`.

---

## [2.9.3] - 2026-08-14
### Added & Completed - PHASE 9.3: Offline Supervisor Synchronization Workspace
- Created domain models in `SurveyWorkspaceModels.kt` (`SyncPackageState`, `SyncPackageWorkspaceItem`, `WorkspaceDashboardStats`, `SyncPackageHistoryRecord`, `DistrictSyncSummary`, `EnumeratorContribution`).
- Upgraded Room database schema to v3 with `SyncPackageEntity`, `SyncPackageHistoryEntity`, and `SyncPackageDao` in `SurveyAppDatabase.kt`.
- Implemented `SupervisorSyncWorkspaceManager.kt` providing offline inbox management, duplicate package rejection via ID and SHA-256, validation and conflict inspection orchestration, state transition logs, real-time offline metrics calculations, and district summary aggregation.
- Created `SupervisorSyncDashboardScreen.kt` featuring Material 3 Arabic RTL layout, 6 summary KPI cards, local survey category breakdown (wells, springs, dams, attachments), filter tabs (All, Inbox, Pending, Merged, Rejected, Archived), package cards with actions and history modals, and district summary dialogs.
- Registered `ScreenRoute.SupervisorSyncDashboard` in `ScreenRoute.kt` and added navigation entry point in `ExportScreen.kt`.
- Added unit and integration test suite `SupervisorSyncWorkspaceTest.kt` verifying multi-package isolation, duplicate rejection, full lifecycle transitions, audit logging, and offline metrics calculation.
- Updated documentation in `PROJECT_STATUS.md`, `DECISIONS_LOG.md`, `CHANGELOG.md`, and `NEXT_TASKS.md`.

---

## [2.9.2] - 2026-08-14
### Added & Completed - PHASE 9.2: Conflict Detection & Controlled Record Merge Engine
- Standardized cross-device survey identity using `surveyUUID` as the primary global key while preserving `recordId` for local identification.
- Extended Room database architecture with `SurveyRecordEntity`, `SurveyRevisionEntity`, `AuditLogEntity`, and `SurveyAttachmentEntity` and their DAOs in `SurveyAppDatabase.kt` (v2).
- Implemented `ConflictDetectionEngine.kt` to classify records into `NEW_RECORD`, `UPDATE_AVAILABLE`, `DUPLICATE`, and `CONFLICT` with deep atomic field differences (`FieldDifference`).
- Implemented `ControlledMergeExecutor.kt` guaranteeing zero silent merging, automatic pre-update historical `SurveyRevisionEntity` snapshots, immutable `AuditLogEntity` recording, and SHA-256 attachment deduplication.
- Created `SurveyMergeReviewScreen.kt` featuring Arabic RTL Material 3 UI, status metric pills, quick batch actions, itemized diff comparisons, and explicit supervisor decision controls (`ACCEPT_INCOMING`, `KEEP_EXISTING`, `REVIEW_LATER`).
- Added unit and integration tests in `ConflictDetectionEngineTest.kt` and `ControlledMergeExecutorTest.kt`.
- Updated documentation in `PROJECT_STATUS.md`, `DECISIONS_LOG.md`, and `CHANGELOG.md`.

---

## [2.9.1] - 2026-08-14
### Added & Completed - PHASE 9.1: Supervisor Survey Package Import & Validation Engine
- Implemented `SurveySyncImporter.kt` featuring sandboxed ZIP unpacking, Zip-Slip attack defense, SHA-256 cryptographic verification, manifest/metadata parsing, and duplicate detection.
- Extended `SurveySyncPackageModels.kt` with `SyncImportPreview` and `SyncPackageInspectionStatus`.
- Created `SurveySyncImportScreen.kt` using Jetpack Compose Material 3 Arabic RTL layout with visual validation badges, duplicate count alerts, itemized survey lists, and manual Accept / Reject controls.
- Integrated `SurveySyncImport` route into `ScreenRoute.kt` and linked from `ExportScreen.kt`.
- Added comprehensive unit tests in `SurveySyncImporterTest.kt` verifying valid package handling, checksum mismatch rejection, missing file rejection, duplicate detection without auto-merge, and offline safety.
- Documented technical decision 011 in `DECISIONS_LOG.md`.

---

## [2.9.0] - 2026-08-14
### Added & Completed - PHASE 9.0: Survey Data Exchange Package Export System (.ywsync)
- Implemented `SurveySyncPackageModels.kt` containing `SurveySyncPackageManifest`, `SyncPackageMetadata`, `SurveyRevisionRecord`, `SyncExportFilter`, `SyncExportResult`, and `SyncPackageValidationResult`.
- Implemented native export engine `SurveySyncExporter.kt` generating signed `.ywsync` ZIP containers with strict Room database read-only semantics.
- Added structured serialization for `manifest.json`, `metadata.json`, `surveys.json`, and `revisions.json`.
- Implemented referenced media isolation copying only images linked by the exported surveys into `attachments/[recordId]/`.
- Built deterministic SHA-256 payload checksum engine and `checksum.sha256` generation.
- Created `SurveySyncExportScreen.kt` using Jetpack Compose, Material 3, and Arabic RTL layout.
- Added comprehensive unit tests in `SurveySyncExporterTest.kt` verifying package generation, ZIP structure, JSON validity, SHA-256 signatures, and filtering.

---

## [2.8.0] - 2026-08-14
### Added & Completed - PHASE 8: Form Package Management System
- Implemented `FormPackageModels.kt` containing `FormPackage`, `PackageMetadata`, `PackageValidationResult`, and `PackageImportResult`.
- Created Room database schema with `FormPackageEntity`, `FormPackageDao`, and `SurveyAppDatabase`.
- Implemented `FormPackageManager.kt` for local package imports (ZIP / directory), required file verification (`metadata.json`, `form_definition.json`, `choices.json`), and optional file verification (`official_template.pdf`, `pdf_mapping.json`).
- Added deterministic SHA-256 directory checksum calculation and safe zip extraction.
- Built `FormManagementScreen.kt` using Jetpack Compose, Material 3, and Arabic RTL layout.
- Added comprehensive unit tests in `FormPackageManagerTest.kt` verifying import, validation, version activation, and survey isolation.

---

## [2.7.0] - 2026-08-14
### Added & Completed - PHASE 7: Official PDF Overlay & Field Coordinate Stamping Engine
- Created coordinate mapping configurations for all three water archetypes: `well_mapping.json`, `spring_mapping.json`, and `dam_mapping.json`.
- Implemented `PdfMappingModels.kt` and `PdfStampingEngine.kt` using Android's native `android.graphics.pdf.PdfDocument`, `Canvas`, and `Paint`.
- Implemented standard A4 canvas dimensions ($595 \times 842$ pt) with high-definition vector background template rendering (Republic of Yemen headers, Ministry of Water titles, structured section boxes, seal and signature boxes).
- Added `PdfTemplatePackage` architecture to support decoupled dynamic survey form packages downloaded to local storage (`form_definition.json`, `official_template.pdf`, `pdf_mapping.json`).
- Standardized single-survey record PDF generation (`{recordId}_Official_{timestamp}.pdf`) without bloat.
- Confirmed attachment separation policy: photos remain separate files linked by `Survey ID` instead of embedded PDF binary blobs.
- Stamped Arabic UTF-8 responses and WGS84 GPS telemetry at exact $(X, Y)$ coordinate points.
- Maintained 100% offline generation saving output PDFs to `context.filesDir/exports/pdfs/`.
- Ensured Room database remains strictly read-only during PDF generation.
- Added PDF export trigger in `ExportScreen.kt`.
- Added unit tests in `PdfStampingEngineTest.kt` verifying coordinate bounds, mapping parser, package-based templates, and generation for Wells, Springs, and Dams.

---

## [2.6.1] - 2026-08-13
### Fixed & Completed - PHASE 6: Genuine Multi-Sheet OOXML (.xlsx) Excel Exporter
- Upgraded `ExcelExporter.kt` to generate genuine ZIP-compressed Office Open XML (`.xlsx`) packages using native `ZipOutputStream`.
- Added the 4th required worksheet: `سجل العمليات (Survey Log)`.
- Added exported data fields across all worksheets: Enumerator ID/Username, GPS Accuracy, GPS Quality, Attachment count, Attachment file references, Workflow Status, and Revision count.
- Updated `ExportScreen.kt` Jetpack Compose UI.
- Upgraded `ExcelExporterTest.kt` unit test verifying OOXML ZIP container entries and XML data mapping.
- Maintained 100% offline generation and Room database read-only immutability.

---

## [2.5.0] - 2026-08-13
### Added - PHASE 5: Automated WGS84 GPS Telemetry & CameraX Media Engine
- Implemented `GpsLocationManager.kt`, `BoundaryValidator.kt`, and `ImageCompressionManager.kt`.
