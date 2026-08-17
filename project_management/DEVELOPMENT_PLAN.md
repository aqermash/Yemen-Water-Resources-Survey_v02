# Development Plan & Roadmap: Yemen Water Survey Field Application

**Project:** Yemen Water Survey Field Application  
**Execution Strategy:** Phase-by-Phase Controlled Development  
**Current Phase:** Phase 0 (Complete) -> Phase 1 (Next)  

---

## Roadmap Overview

```
Phase 0: System Analysis & Architecture Setup  [COMPLETED]
   │
   ▼
Phase 1: Application Foundation & Mobile Frame UI Shell
   │
   ▼
Phase 2: Offline SQLite Database Engine & Data Models
   │
   ▼
Phase 3: Role-Based User Management & Security System
   │
   ▼
Phase 4: Dynamic Form Engine (XLSForm / ODK Parser)
   │
   ▼
Phase 5: Automated WGS84 GPS Telemetry & Photo Attachment Engine
   │
   ▼
Phase 6: Multi-Sheet Excel Exporter (.xlsx)
   │
   ▼
Phase 7: Official PDF Overlay & Field Coordinate Generator
   │
   ▼
Phase 8: Administrative Reference System & Location Change Requests
   │
   ▼
Phase 9: Performance Optimization, Stress Testing & Final Verification
```

---

## Detailed Phase Breakdown

### PHASE 0: System Analysis & Architecture Setup
- **Status:** COMPLETED
- **Objectives:** Analyze functional requirements, define Clean Architecture layers, formulate SQLite schema, design dynamic form engine specs, coordinate PDF mapper rules, and set up documentation infrastructure.
- **Expected Outputs:** Architectural blueprints, database schemas, development plan, security matrix.
- **Created Files:**
  - `/metadata.json`
  - `/project_management/PROJECT_STATUS.md`
  - `/project_management/ARCHITECTURE.md`
  - `/project_management/DEVELOPMENT_PLAN.md`
  - `/project_management/DECISIONS_LOG.md`
  - `/project_management/CHANGELOG.md`
  - `/project_management/KNOWN_ISSUES.md`
  - `/project_management/NEXT_TASKS.md`
- **Modified Files:** `/metadata.json`
- **Testing Requirements:** Verification of requirements coverage against Yemen water survey standards.
- **Completion Criteria:** All core documentation files created and validated.

---

### PHASE 1: Application Foundation & Mobile Frame UI Shell
- **Status:** PLANNED (NEXT TASK)
- **Objectives:** Establish the core application directory structure, mobile viewport frame wrapper, navigation routing, Arabic RTL theme context, and base dashboard interface.
- **Expected Outputs:** Fully launching mobile shell with offline status banner, navigation bar, and Arabic UI scaffolding.
- **Created Files:**
  - `/src/core/theme/rtlTheme.ts`
  - `/src/core/navigation/routes.ts`
  - `/src/presentation/components/MobileContainer.tsx`
  - `/src/presentation/components/OfflineBanner.tsx`
  - `/src/presentation/screens/DashboardScreen.tsx`
- **Modified Files:**
  - `/src/App.tsx`
- **Testing Requirements:** Application boots without errors, layout renders correctly in Arabic RTL mode, screen transitions respond accurately.
- **Completion Criteria:** Clean UI launch with working mock routes and responsive layout.

---

### PHASE 2: Offline SQLite Data Engine & Repositories
- **Status:** PLANNED
- **Objectives:** Implement local SQLite data storage engine (or IndexedDB wrapper for web sandbox execution), create typed entity data models, repository abstractions, and migration scripts.
- **Expected Outputs:** Fully working local database manager with CRUD operations for surveys, attachments, and user profiles.
- **Created Files:**
  - `/src/data/db/schema.ts`
  - `/src/data/db/sqliteEngine.ts`
  - `/src/data/models/SurveyModel.ts`
  - `/src/data/repositories/SurveyRepository.ts`
- **Modified Files:**
  - `/src/App.tsx`
- **Testing Requirements:** Insert, read, update, and soft-delete test records locally without network access.
- **Completion Criteria:** Verified local storage persistence across app restarts.

---

### PHASE 3: Role-Based User Management & Local Authentication
- **Status:** PLANNED
- **Objectives:** Implement user login, PIN/password hashing, local session management, role-based capability filtering (Enumerator, District Supervisor, Governorate Supervisor, Central Admin).
- **Expected Outputs:** Login screen, local authentication service, role context provider, profile view.
- **Created Files:**
  - `/src/domain/services/AuthService.ts`
  - `/src/presentation/screens/LoginScreen.tsx`
  - `/src/presentation/components/RoleGuard.tsx`
- **Modified Files:**
  - `/src/presentation/screens/DashboardScreen.tsx`
- **Testing Requirements:** Test logging in as different roles; confirm district supervisors only access their district records while central admins see all records.
- **Completion Criteria:** Role enforcement active and verified.

---

### PHASE 4: Dynamic Form Engine (XLSForm / ODK Parser)
- **Status:** PLANNED
- **Objectives:** Build parser for XLSForm question types (Wells, Springs, Dams), including choice lists, skip logic (`relevant`), field constraints, required fields, and repeat groups.
- **Expected Outputs:** Dynamic form renderer generating intuitive Arabic fields from JSON/XML form packages.
- **Created Files:**
  - `/src/domain/formEngine/XlsFormParser.ts`
  - `/src/domain/formEngine/RuleEvaluator.ts`
  - `/src/presentation/screens/DynamicSurveyScreen.tsx`
  - `/src/assets/forms/Well_Form.json`
  - `/src/assets/forms/Spring_Form.json`
  - `/src/assets/forms/Dam_Form.json`
- **Modified Files:**
  - `/src/core/navigation/routes.ts`
- **Testing Requirements:** Render Wells, Springs, and Dams forms dynamically; test skip logic when choosing pumping mechanisms or water clarity options.
- **Completion Criteria:** Complete data validation and response capture for all 3 survey types.

---

### PHASE 5: Automated WGS84 GPS Telemetry & Media Attachment Engine
- **Status:** PLANNED
- **Objectives:** Integrate automatic WGS84 GPS location capture (Latitude, Longitude, Altitude, Accuracy) and camera image attachment with client-side JPEG compression and EXIF metadata link.
- **Expected Outputs:** GPS lock widget with real-time accuracy display and photo capture manager.
- **Created Files:**
  - `/src/core/services/GpsService.ts`
  - `/src/core/services/CameraMediaService.ts`
  - `/src/presentation/components/GpsCaptureWidget.tsx`
  - `/src/presentation/components/PhotoPickerWidget.tsx`
- **Modified Files:**
  - `/src/presentation/screens/DynamicSurveyScreen.tsx`
- **Testing Requirements:** Simulate GPS capture with coordinates; verify image file compression (<300KB) and link creation in attachments table.
- **Completion Criteria:** GPS telemetry and compressed image files stored and linked to survey records.

---

### PHASE 6: Multi-Sheet Excel Exporter (`.xlsx`)
- **Status:** PLANNED
- **Objectives:** Build custom Excel workbook exporter generating structured spreadsheets containing `Wells`, `Springs`, `Dams`, and `Survey Log` worksheets.
- **Expected Outputs:** Downloadable/exportable `.xlsx` files with complete administrative codes, enumerator metadata, and technical measurements.
- **Created Files:**
  - `/src/core/services/ExcelExportService.ts`
  - `/src/presentation/screens/ExportScreen.tsx`
- **Modified Files:**
  - `/src/presentation/screens/DashboardScreen.tsx`
- **Testing Requirements:** Generate sample survey exports and verify file structure in spreadsheet viewers.
- **Completion Criteria:** Valid Excel file generation matching official survey schema.

---

### PHASE 7: Official PDF Overlay & Field Coordinate Mapping Generator
- **Status:** PLANNED
- **Objectives:** Implement stamped PDF generation engine that fills official survey document templates using field coordinate mapping rules (`Well_Mapping.json`).
- **Expected Outputs:** On-demand printable PDF survey sheets formatted to exact government archive dimensions.
- **Created Files:**
  - `/src/core/services/PdfFillService.ts`
  - `/src/assets/pdfTemplates/Well_Mapping.json`
  - `/src/assets/pdfTemplates/Spring_Mapping.json`
  - `/src/assets/pdfTemplates/Dam_Mapping.json`
- **Modified Files:**
  - `/src/presentation/screens/RecordsManagerScreen.tsx`
- **Testing Requirements:** Trigger PDF export for a completed record; confirm response text is stamped in correct coordinates without layout degradation.
- **Completion Criteria:** Crisp, printable Arabic PDF document generated on request.

---

### PHASE 8: Administrative Reference Database & Location Change Requests
- **Status:** PLANNED
- **Objectives:** Load official Yemen Administrative Reference data (Governorates, Districts, Uzlahs, Villages) and create field Change Request flow for missing/modified localities.
- **Expected Outputs:** Cascading location selection drop-downs and supervisor change request review panel.
- **Created Files:**
  - `/src/data/reference/adminDataYemen.ts`
  - `/src/presentation/components/AdminLocationSelector.tsx`
  - `/src/presentation/screens/AdminChangeRequestsScreen.tsx`
- **Modified Files:**
  - `/src/presentation/screens/DynamicSurveyScreen.tsx`
- **Testing Requirements:** Verify cascading choices (selecting 'Saada' filters districts to 'Sahar', etc.); test submitting a new village change request.
- **Completion Criteria:** Cascading admin lookup operational and change request workflow functional.

---

### PHASE 9: Performance Optimization, Audit Logging & Stress Verification
- **Status:** PLANNED
- **Objectives:** Implement immutable audit logging, soft-delete management, database indexing, and test performance with thousands of local records.
- **Expected Outputs:** Fast, memory-efficient application capable of storing large offline survey datasets without lag.
- **Created Files:**
  - `/src/core/services/AuditLogger.ts`
  - `/src/presentation/screens/AuditLogScreen.tsx`
- **Modified Files:**
  - `/src/data/repositories/SurveyRepository.ts`
- **Testing Requirements:** Stress test database with 1,000+ mock survey records; verify sub-100ms query performance and soft delete behavior.
- **Completion Criteria:** Application passes all performance, security, and integrity tests.
