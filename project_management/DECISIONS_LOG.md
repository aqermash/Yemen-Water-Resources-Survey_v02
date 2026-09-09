# Technical Decisions Log: Yemen Water Survey Field Application

**Project Name:** Yemen Water Survey Field Application  
**Documentation Version:** 1.0.0  

---

## Decision 001: Offline-First Architecture with Zero Central Server Dependency
- **Date:** 2026-08-13
- **Context:** Surveyors operate in remote areas across Yemen with destroyed infrastructure, absent cellular coverage, or strict network isolation.
- **Decision:** Build a self-contained offline application using local SQLite/IndexedDB storage. Eliminates mandatory cloud API requirements, central server connections, VPNs, or online sync tokens for primary data collection.
- **Alternatives Considered:**
  - *Cloud-first with sync queue:* Rejected due to unreliable connectivity resulting in app lockouts or lost survey entries during network dropouts.
  - *Hybrid CouchDB/PouchDB:* Rejected due to heavy memory overhead on lower-end Android devices.
- **Consequences:** All data validation, PDF generation, and Excel formatting must occur client-side on the device.

---

## Decision 002: Dynamic Form Engine based on XLSForm / ODK Standard
- **Date:** 2026-08-13
- **Context:** Water survey questionnaires evolve based on ministerial mandates, regional conditions, or donor reporting criteria.
- **Decision:** Construct a dynamic Form Engine capable of consuming JSON/XML schemas parsed from XLSForm standards instead of hardcoding inputs.
- **Alternatives Considered:**
  - *Hardcoded Form UI Screens:* Rejected because altering survey questions would require rebuilding and distributing a new APK to all field teams.
- **Consequences:** The application remains forward-compatible with future water infrastructure survey formats.

---

## Decision 003: PDF Coordinate Overlay Stamping Engine
- **Date:** 2026-08-13
- **Context:** Government archives in Yemen require survey documents to adhere strictly to pre-printed official ministerial PDF forms.
- **Decision:** Implement an overlay engine using exact (X, Y) page coordinates (`Well_Mapping.json`) to stamp text onto original PDF background templates.
- **Alternatives Considered:**
  - *HTML-to-PDF Conversion:* Rejected because generated layouts differ slightly from official government form geometry and stamps.
- **Consequences:** Precise pixel/point coordinate mappings must be maintained for each official form type.

---

## Decision 004: Soft Delete Pattern for Survey Records
- **Date:** 2026-08-13
- **Context:** Accidental or unauthorized record deletion in the field leads to lost survey data and audit discrepancies.
- **Decision:** Implement soft deletion (`is_deleted = 1`) across the database. Only Governorate Supervisors and Central Admins can mark records as deleted; records are never permanently purged from the local database by standard users.
- **Alternatives Considered:**
  - *Hard Delete (`DELETE FROM`):* Rejected due to risk of data loss and lack of audit capability.
- **Consequences:** All database queries must include `WHERE is_deleted = 0` filtering unless auditing deleted items.

---

## Decision 005: Code-First Administrative Reference with Change Request Flow
- **Date:** 2026-08-13
- **Context:** Administrative boundaries (Governorate -> District -> Uzlah -> Village) in Yemen have official numerical codes, but field surveyors often encounter renamed or unlisted villages.
- **Decision:** Enforce unique codes as the primary key in database tables while displaying Arabic names. Provide an "Administrative Change Request" module allowing surveyors to propose new or renamed locations without breaking system code references.
- **Alternatives Considered:**
  - *Free-text location entry:* Rejected because it causes duplicate, inconsistent, and unstandardized location names across surveys.
- **Consequences:** Surveyors select locations via cascading code pickers and submit formal change requests when discrepancies arise.

---

## Decision 006: PDF is an Official Output & Archival Format (Not Survey Input)
- **Date:** 2026-08-14
- **Context:** Survey data collection must be driven dynamically by XLSForm/JSON schemas rather than static PDF forms.
- **Decision:** Establish that PDF is strictly an official output and print/archival format (`Survey Data + Official Template + PDF Mapping -> Filled Official PDF`). The dynamic survey engine handles all data entry independently of the PDF format.
- **Consequences:** PDF rendering is completely decoupled from survey intake logic.

---

## Decision 007: Package-Based Survey and PDF Templates
- **Date:** 2026-08-14
- **Context:** Survey questionnaires and ministerial template requirements are subject to updates across provinces without requiring APK re-compilation.
- **Decision:** Adopt a package-based structure for survey definitions and PDF templates (`form_definition.json`, `choices.json`, `metadata.json`, `official_template.pdf`, `pdf_mapping.json`). `PdfStampingEngine` accepts `PdfTemplatePackage` from local storage.
- **Consequences:** Templates can be updated dynamically via offline distribution packages.

---

## Decision 008: Single Survey Record per PDF and Attachment Separation Policy
- **Date:** 2026-08-14
- **Context:** Generating massive multi-thousand-page PDFs exhausts mobile device memory and complicates individual survey archiving. Embedding raw photo binaries bloats PDF sizes.
- **Decision:** Exactly one survey record generates one official PDF document (e.g. `WELL-YE-SA-D05-E03-000001_Official.pdf`). Photos and attachments remain separate filesystem assets linked by `Survey ID` and are not embedded into the PDF.
- **Consequences:** High performance on lower-end Android devices, deterministic file naming, and seamless integration with future hierarchical export packages.

---

## Decision 009: Form Package Management and Room Schema Isolation
- **Date:** 2026-08-14
- **Context:** Managing multiple versions of survey questionnaires across field teams requires dynamic importing, validation, and version activation without mutating existing survey data tables.
- **Decision:** Implement `FormPackageManager` with strict package validation (verifying `metadata.json`, `form_definition.json`, `choices.json`, `official_template.pdf`, `pdf_mapping.json`) and store package metadata in dedicated `form_packages` Room database entity (`FormPackageEntity`).
- **Consequences:** Survey questionnaires are completely decoupled from APK builds, version activations are atomic, and existing survey records remain 100% immutable and intact.

---

## Decision 010: Offline Survey Data Exchange Package Specification (.ywsync)
- **Date:** 2026-08-14
- **Context:** Field surveyors operate in conflict-affected regions across Yemen with zero internet connectivity. Data transfer between Enumerator, District Supervisor, Governorate Supervisor, and Central HQ must happen completely offline via physical media (USB OTG, SD cards) or local wireless transfers (Bluetooth / Wi-Fi Direct).
- **Decision:** Establish a standardized, transport-independent ZIP archive format (`.ywsync`) containing `manifest.json`, `metadata.json`, `surveys.json`, `revisions.json`, isolated `attachments/[recordId]/` directory, and signed with deterministic `checksum.sha256`. The exporter (`SurveySyncExporter.kt`) operates with strict Room database read-only semantics.
- **Consequences:** Data exchange is 100% offline, cryptographically verifiable, eliminates unreferenced photo bloat, and provides a clear foundation for Phase 9.1 import and merge operations.

---

## Decision 011: Strict Isolated Sandbox Package Inspection and Zero Auto-Mutation
- **Date:** 2026-08-14
- **Context:** Supervisor devices receive unverified `.ywsync` files from field staff across different districts. Importing tampered, malformed, or duplicate packages directly into the active Room database could cause silent record corruption, data loss, or overwrite existing verified surveys.
- **Decision:** Implement a strict sandbox staging mechanism (`SurveySyncImporter.kt`). All packages are unpacked into isolated temporary cache storage protected against Zip-Slip traversal. SHA-256 integrity signatures and JSON schemas are verified before presenting a read-only `SyncImportPreview`. Duplicate detection (by Survey UUID and Package ID) alerts the supervisor without executing automatic merges or database mutations. Data is only staged (`staged_packages/`) upon explicit manual supervisor confirmation.
- **Alternatives Considered:**
  - *Automatic background insertion on file drop:* Rejected because it prevents supervisors from reviewing duplicate conflicts, corrupted files, or out-of-district submissions.
- **Consequences:** Guarantee of zero unintended database mutations, deterministic duplicate detection, protection against corrupted/tampered payloads, and full supervisor oversight.

---

## Decision 013: Supervisor Synchronization Workspace with Multi-Package Isolation & District Summaries
- **Date:** 2026-08-14
- **Context:** Supervisors oversee multiple enumerators in a district, receiving separate `.ywsync` packages via flash drives, SD cards, or offline Wi-Fi Direct. Managing these packages through one-off screens without state tracking leads to duplicate imports, lost packages, and inability to view district-level progress.
- **Decision:**
  1. Create a dedicated Supervisor Workspace (`SupervisorSyncWorkspaceManager.kt` & `SupervisorSyncDashboardScreen.kt`).
  2. Implement Room entities `SyncPackageEntity` and `SyncPackageHistoryEntity` (Database v3) to persist package metadata, state (`RECEIVED`, `VALIDATED`, `REVIEW_PENDING`, `PARTIALLY_MERGED`, `MERGED`, `REJECTED`, `ARCHIVED`), and immutable transition histories.
  3. Prevent duplicate imports deterministically by checking both Package ID and payload SHA-256 hash against existing records.
  4. Compute real-time dashboard KPIs (`WorkspaceDashboardStats`) and district-level database synchronization summaries (`DistrictSyncSummary`) strictly offline from local Room tables.
  5. Enforce complete multi-package isolation: packages from different enumerator devices remain separate in the supervisor inbox until explicitly reviewed and merged.
- **Consequences:** Supervisors have a comprehensive offline cockpit for tracking all incoming packages, reviewing enumerator contributions, monitoring conflict queues, and compiling district reports without internet connectivity.

---

## Decision 014: Authoritative OCHA P-Codes, Controlled Local Overrides, and Offline GIS Engine
- **Date:** 2026-08-15
- **Context:** Nationwide field operations require one unified administrative identity system across Yemen (22 Governorates, 333 Districts, 2,146 Uzlahs, ~41,494 Villages). Datasets from OCHA, Yemen-Info, and TopoJSON contain varying codes, alternate spellings, and informal village names. Mutating official datasets or inventing arbitrary P-codes would destroy interoperability with national water authorities and UN clusters.
- **Decision:**
  1. **Authoritative Standard:** OCHA/IMMAP P-codes (`yem_admin_pcodes-02122024.xlsx`) are established as the immutable single source of truth for administrative P-codes down to Uzlah (Admin3).
  2. **Yemen-Info Enrichment & Zero Fabricated P-Codes:** Yemen-Info dataset (`yemen-info.json`) is used strictly for enrichment (village localities, Arabic Tashkeel, alternative names). Villages NEVER receive fabricated OCHA P-codes; unmapped villages are explicitly tagged `UNMAPPED` or `NEEDS_REVIEW`.
  3. **Controlled Local Overrides:** The application strictly forbids overwriting or mutating official OCHA records. A dedicated `admin_overrides` entity allows supervisors to record local/colloquial renamings, which are displayed alongside official names while keeping official P-codes and names recoverable at all times.
  4. **Offline GIS Point-in-Polygon Engine:** TopoJSON geometries are indexed in Room (`admin_geometry`) with bounding boxes (`minLat`, `maxLat`, `minLon`, `maxLon`) for O(1) candidate rejection and resolved using the Ray-Casting algorithm 100% offline without external GIS dependencies.
- **Consequences:** Deterministic administrative hierarchy, full spatial consistency validation between GPS coordinates and selected administrative units, zero network dependency, and complete historical auditability.

---

## Decision 015: Production Administrative Reference Data Authority and Cross-Mapping Policy
- **Date:** 2026-08-15
- **Context:** Integrating production datasets across OCHA XLSX, Yemen-Info JSON, and TopoJSON requires deterministic data authority rules, multi-stage cross-mapping algorithms, and uncompromised provenance tracking. Field reality includes informal administrative naming and newly established localities that do not match the official 2024 OCHA baseline.
- **Decision:**
  1. **Data Authority Hierarchy:**
     - Level 1: OCHA/IMMAP P-code reference (Immutable foundation).
     - Level 2: OCHA administrative names and boundaries (Authoritative official boundaries).
     - Level 3: Yemen-Info village/locality enrichment (Locality names, Tashkeel, alternative spellings).
     - Level 4: Local approved administrative overrides (Field reality and community usage).
  2. **Multi-Stage Cross-Mapping Engine:** Yemen-Info records are cross-mapped to OCHA P-codes through a 6-stage pipeline: (1) Explicit ID linkage, (2) Parent hierarchy match, (3) Arabic text normalization (Tashkeel stripping, character unification, prefix removal), (4) English transliteration normalization, (5) Geographic proximity & bounding-box evaluation, and (6) Ambiguity/Review flagging.
  3. **Zero Fabricated P-Codes & Provenance Integrity:** Unmapped or ambiguous records retain `admin3Pcode = null` and are explicitly categorized as `UNMAPPED`, `AMBIGUOUS`, or `NEEDS_REVIEW`. Yemen-Info IDs and raw provenance are permanently preserved in `VillageEntity`.
  4. **Production Reference Packaging:** Administrative reference packages are versioned (`versionTag`), verified using SHA-256 integrity checksums, and distributed offline via portable files without cloud or network requirements.
- **Consequences:** Eliminates administrative ambiguity, preserves interoperability with national water cluster standards, accurately reflects field reality via separate override layers, and ensures zero risk of data corruption from unverified administrative claims.

---

## Decision 016: Dynamic XLSForm Engine Integration, Legacy Screen Role, and Expression Engine Baseline

**Date:** 2026-09-07  
**Status:** Approved  
**Supersedes:** None  
**Related Decisions:** Decision 002 (Dynamic XLSForm Form Engine Architecture), Decision 014 (Survey Screen Architecture)

### Context
Commit `ca51b0e` integrated the dynamic XLSForm-driven form engine (`DynamicSurveyScreen`, `DynamicFormRenderer`, `FormPackageManager`, `ExpressionEvaluatorEngine`) into the application navigation flow (`MainActivity.kt`), routing all survey creation and editing to the dynamic runtime.

Following this integration, an architectural and diagnostic audit was conducted to clarify:
1. The role of the three legacy hardcoded survey screens (`NewWellSurveyScreen.kt`, `NewSpringSurveyScreen.kt`, `NewDamSurveyScreen.kt`).
2. The runtime status of the expression evaluator engine and its function support across form schema versions.
3. The validation state of the dynamic form runtime commit (`ca51b0e`) on top of Database schema v7 (`e7e0778`).

### Decision
1. **Architectural Target Reaffirmed:** Decision 002 remains the confirmed end-state architecture. The survey workflow is driven by the dynamic XLSForm-driven form engine.

2. **Legacy Screen Role:** The three hardcoded survey screens (`NewWellSurveyScreen.kt`, `NewSpringSurveyScreen.kt`, `NewDamSurveyScreen.kt`) are retained strictly as **transitional reference implementations** and fallback comparisons. They are not active in `MainActivity.kt` navigation.

3. **Authoritative Form Set & Expression Support:**
   - The authoritative survey definitions are the v7 XLSX forms:
     - `forms/yem_water_wells_v7.xlsx`
     - `forms/yem_water_springs_v7.xlsx`
     - `forms/yem_water_harvesting_v7.xlsx`
   - The expression evaluator engine (`ExpressionEvaluatorEngine.kt`) supports core arithmetic, comparison, logical operators, variable substitution, and string functions including `concat()`.
   - String concatenation (`concat()`), which was utilized in historical v6 calculations, is not used in the authoritative v7 forms, but support is retained in the engine.

4. **Validation Status:** Commit `ca51b0e` successfully wires the dynamic form engine to the navigation graph and database layer. Comprehensive field validation, full multi-page widget rendering tests, and supervisor end-to-end operational workflows remain in progress.

### Consequences
- **Positive:** Clear architectural boundaries between legacy reference code and active dynamic runtime; authoritative form schemas verified at v7; no ambiguity regarding expression engine capabilities.
- **Negative / Debt:** Unused legacy survey screens remain in the codebase as reference debt until formal deprecation/removal in a future cleanup phase.








