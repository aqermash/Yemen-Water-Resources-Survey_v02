# System Architecture: Yemen Water Survey Field Application

**Project Name:** Yemen Water Survey Field Application (تطبيق المسح الميداني للمياه - اليمن)  
**Architecture Pattern:** Clean Architecture + Offline-First Local Data Engine  
**Target Platform:** Mobile-First Android Application / PWA with Offline Storage  

---

## 1. System Architectural Overview

The application is structured according to **Clean Architecture** principles, enforcing strict separation of concerns, testability, and independence from external frameworks or network connections.

```
+-----------------------------------------------------------------------+
|                       PRESENTATION LAYER                              |
|   Arabic RTL UI / Mobile Views / Form Controllers / Dashboard / Maps  |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                     BUSINESS LOGIC (DOMAIN) LAYER                     |
|  Survey Use Cases / Form Engine / Rules Evaluator / Export Services   |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                            DATA LAYER                                 |
|  Repositories / Data Sources / Entity Mappers / Migration Engines     |
+-----------------------------------+-----------------------------------+
                                    |
                                    v
+-----------------------------------------------------------------------+
|                     CORE SERVICES & LOCAL STORAGE                     |
|  SQLite DB / IndexedDB / Camera & Image Compressor / WGS84 GPS Engine |
+-----------------------------------------------------------------------+
```

### Key Architectural Layers:
1. **Presentation Layer (`/presentation` or `/features`):**
   - Arabic RTL interface components designed for high legibility under sunlight.
   - Screen Modules: Login, Dashboard, Survey Type Selector, Dynamic XLSForm Renderer, Record Manager, Admin Change Request Manager, Export Screen, Settings & Audit Logs.
   - State Management: Reactive state streams with localized error boundary handling.

2. **Domain / Business Logic Layer (`/domain` or `/usecases`):**
   - Form Engine Logic: Parses XLSForm/ODK structures, evaluates field dependencies (`relevant`), performs calculations (`calculate`), and enforces constraints (`constraint`).
   - Role Permission Evaluator: Determines accessible functions based on Enumerator, District Supervisor, Governorate Supervisor, or Central Admin roles.
   - PDF Field Mapper: Maps survey field data to target page numbers and (X, Y) coordinate vectors on official PDF templates.

3. **Data Layer (`/data`):**
   - Repositories: `SurveyRepository`, `UserRepository`, `AdminRefRepository`, `MediaRepository`, `ExportRepository`.
   - Data Sources: SQLite Local Database, Local Encrypted Storage, Local File Storage for images and generated PDF/Excel files.

4. **Core Services Layer (`/core`):**
   - `GpsService`: Hardware WGS84 auto-capture (Latitude, Longitude, Altitude, Accuracy, Timestamp).
   - `CameraMediaService`: Image capture, EXIF metadata tagger, automated downsampling & JPEG compression.
   - `ExcelExportService`: Generates formatted multi-sheet `.xlsx` files (`Wells`, `Springs`, `Dams`, `Survey Log`).
   - `PdfFillService`: Generates crisp printable PDFs by stamping survey responses directly onto original template layouts.

---

## 2. Complete SQLite Database Schema

The local SQLite database (`yemen_water_survey.db`) is protected against accidental deletion, uses WAL (Write-Ahead Logging) mode, and enforces foreign key constraints.

### 2.1 Table Definitions

#### 1. `users` Table
Stores local accounts and credentials.
```sql
CREATE TABLE users (
    user_id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name_ar TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('Enumerator', 'District_Supervisor', 'Governorate_Supervisor', 'Central_Admin')),
    governorate_code TEXT NOT NULL,
    district_code TEXT,
    team_code TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
```

#### 2. `forms` Table
Stores external XLSForm / ODK dynamic form definitions and versioning metadata.
```sql
CREATE TABLE forms (
    form_id TEXT PRIMARY KEY,
    form_type TEXT NOT NULL CHECK (form_type IN ('Well', 'Spring', 'Dam')),
    version TEXT NOT NULL,
    title_ar TEXT NOT NULL,
    form_json TEXT NOT NULL, -- Complete parsed XLSForm question structure & choices
    pdf_mapping_json TEXT NOT NULL, -- PDF field placement coordinates (page, x, y)
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL
);
```

#### 3. `survey_records` Table
Master table for all submitted surveys. Uses **Soft Delete** (`is_deleted = 1`).
```sql
CREATE TABLE survey_records (
    record_id TEXT PRIMARY KEY,
    form_id TEXT NOT NULL,
    survey_type TEXT NOT NULL CHECK (survey_type IN ('Well', 'Spring', 'Dam')),
    governorate_code TEXT NOT NULL,
    district_code TEXT NOT NULL,
    uzlah_code TEXT NOT NULL,
    village_code TEXT NOT NULL,
    locality_name TEXT,
    enumerator_id TEXT NOT NULL,
    enumerator_username TEXT NOT NULL,
    device_info TEXT NOT NULL,
    app_version TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'Completed' CHECK (status IN ('Draft', 'Completed', 'Approved', 'Flagged')),
    is_deleted INTEGER NOT NULL DEFAULT 0,
    deleted_at TEXT,
    deleted_by TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (form_id) REFERENCES forms(form_id),
    FOREIGN KEY (enumerator_id) REFERENCES users(user_id)
);
```

#### 4. `wells` Table
Detailed technical responses for Well surveys (آبار المياه).
```sql
CREATE TABLE wells (
    well_id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL UNIQUE,
    well_name_ar TEXT NOT NULL,
    well_type TEXT NOT NULL, -- Artesian (ارتوازي), Hand-dug (يدوي), etc.
    well_depth_m REAL,
    water_level_m REAL,
    casing_diameter_inch REAL,
    yield_liters_per_sec REAL,
    salinity_ppm REAL,
    pumping_mechanism TEXT, -- Solar, Diesel, Electric, Manual
    operational_status TEXT NOT NULL, -- Functional, Non-functional, Seasonally Functional
    owner_type TEXT NOT NULL, -- Public, Private, Community, NGO
    owner_name TEXT,
    beneficiaries_count INTEGER,
    use_category TEXT, -- Drinking, Irrigation, Multi-use
    notes TEXT,
    FOREIGN KEY (record_id) REFERENCES survey_records(record_id) ON DELETE CASCADE
);
```

#### 5. `springs` Table
Detailed technical responses for Spring surveys (العيون والينابيع).
```sql
CREATE TABLE springs (
    spring_id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL UNIQUE,
    spring_name_ar TEXT NOT NULL,
    flow_rate_lps REAL,
    water_clarity TEXT, -- Clear, Turbid, Saline
    discharge_seasonality TEXT, -- Perennial (دائم), Seasonal (موسمي)
    protection_structure TEXT, -- Protected basin, Open, Damaged
    beneficiaries_count INTEGER,
    primary_use TEXT,
    notes TEXT,
    FOREIGN KEY (record_id) REFERENCES survey_records(record_id) ON DELETE CASCADE
);
```

#### 6. `dams` Table
Detailed technical responses for Dams & Water Harvesting structures (السدود والحواجز المائية).
```sql
CREATE TABLE dams (
    dam_id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL UNIQUE,
    dam_name_ar TEXT NOT NULL,
    structure_type TEXT NOT NULL, -- Storage Dam (سد تخزيني), Diversion Barrier (حاجز تحويلي), Cistern (بركة)
    storage_capacity_m3 REAL,
    current_water_volume_m3 REAL,
    dam_height_m REAL,
    spillway_condition TEXT,
    sedimentation_level TEXT, -- Low, Medium, High
    structural_condition TEXT, -- Good, Needs Repair, Critical
    command_area_hectares REAL,
    notes TEXT,
    FOREIGN KEY (record_id) REFERENCES survey_records(record_id) ON DELETE CASCADE
);
```

#### 7. `attachments` Table
Metadata links for captured media files (Photos, Scans, Notes).
```sql
CREATE TABLE attachments (
    attachment_id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL,
    file_type TEXT NOT NULL CHECK (file_type IN ('Photo', 'Document', 'Sketch')),
    file_path TEXT NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    mime_type TEXT NOT NULL,
    caption TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (record_id) REFERENCES survey_records(record_id) ON DELETE CASCADE
);
```

#### 8. `gps_data` Table
High-precision WGS84 location telemetry attached to survey records.
```sql
CREATE TABLE gps_data (
    gps_id TEXT PRIMARY KEY,
    record_id TEXT NOT NULL UNIQUE,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    altitude_m REAL,
    accuracy_m REAL NOT NULL,
    captured_at TEXT NOT NULL,
    provider TEXT NOT NULL, -- GPS, Network
    FOREIGN KEY (record_id) REFERENCES survey_records(record_id) ON DELETE CASCADE
);
```

#### 9. `admin_reference` Table
Administrative divisions lookup table (Governorate -> District -> Uzlah -> Village -> Locality).
```sql
CREATE TABLE admin_reference (
    admin_id TEXT PRIMARY KEY,
    governorate_code TEXT NOT NULL,
    governorate_name_ar TEXT NOT NULL,
    district_code TEXT NOT NULL,
    district_name_ar TEXT NOT NULL,
    uzlah_code TEXT NOT NULL,
    uzlah_name_ar TEXT NOT NULL,
    village_code TEXT NOT NULL,
    village_name_ar TEXT NOT NULL,
    locality_name_ar TEXT
);
CREATE INDEX idx_admin_ref_lookup ON admin_reference (governorate_code, district_code, uzlah_code, village_code);
```

#### 10. `admin_change_requests` Table
Holds surveyor proposals for missing or renamed administrative locations.
```sql
CREATE TABLE admin_change_requests (
    request_id TEXT PRIMARY KEY,
    enumerator_id TEXT NOT NULL,
    admin_level TEXT NOT NULL CHECK (admin_level IN ('Governorate', 'District', 'Uzlah', 'Village', 'Locality')),
    parent_code TEXT NOT NULL,
    old_name_ar TEXT,
    proposed_name_ar TEXT NOT NULL,
    reason TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    status TEXT NOT NULL DEFAULT 'Pending_Review' CHECK (status IN ('Pending_Review', 'Approved', 'Rejected')),
    created_at TEXT NOT NULL,
    reviewed_at TEXT,
    reviewed_by TEXT
);
```

#### 11. `export_history` Table
Logs generated Excel/PDF files and export tasks.
```sql
CREATE TABLE export_history (
    export_id TEXT PRIMARY KEY,
    export_type TEXT NOT NULL CHECK (export_type IN ('Excel', 'PDF')),
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    records_count INTEGER NOT NULL,
    generated_by TEXT NOT NULL,
    created_at TEXT NOT NULL
);
```

#### 12. `audit_logs` Table
Immutable security audit log tracking user authentication, record additions, edits, soft deletes, and exports.
```sql
CREATE TABLE audit_logs (
    log_id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    action TEXT NOT NULL, -- LOGIN, CREATE_SURVEY, SOFT_DELETE_SURVEY, EXPORT_EXCEL, GENERATE_PDF, CHANGE_REQUEST
    details TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    ip_or_device TEXT NOT NULL
);
```

---

## 3. Dynamic Form Engine (XLSForm / ODK Specification)

To prevent hard-coding survey fields, forms are loaded dynamically from structured XLSForm packages (`Form.xlsx` / `Form.json`).

### Form Specification Matrix:
- **Questions Sheet (`survey`):**
  - `type`: `text`, `integer`, `decimal`, `select_one [list_name]`, `select_multiple [list_name]`, `date`, `geopoint`, `image`, `note`, `begin_repeat`, `end_repeat`.
  - `name`: Unique internal field key (e.g., `well_depth_m`).
  - `label::Arabic (ar)`: Display title in Arabic (e.g., `عمق البئر بالمتر`).
  - `hint::Arabic (ar)`: Field hint text.
  - `required`: `true` / `false`.
  - `relevant`: Expression for skip logic (e.g., `${pumping_mechanism} = 'diesel'`).
  - `constraint`: Validation formula (e.g., `${well_depth_m} > 0 and ${well_depth_m} < 2000`).
  - `constraint_message::Arabic`: Error text when validation fails.

---

## 4. Official PDF Template & Coordinates Mapping Engine

Official water survey forms in Yemen require exact visual reproduction for municipal and administrative archives.

### Workflow Architecture:
1. **Source PDF Template:** High-resolution digital scan of official paper form (`Well_Template.pdf`).
2. **JSON Field Mapping (`Well_Mapping.json`):** Defines bounding boxes and (X, Y) coordinates in PostScript points (1/72 inch).
```json
{
  "template_pdf": "Forms/Wells/Well_Template.pdf",
  "page_dimensions": { "width": 595.28, "height": 841.89 },
  "fields": [
    { "key": "well_name_ar", "page": 1, "x": 380.0, "y": 720.0, "fontSize": 10, "font": "Amiri-Regular", "align": "right" },
    { "key": "governorate_name_ar", "page": 1, "x": 420.0, "y": 750.0, "fontSize": 11, "font": "Amiri-Bold", "align": "right" },
    { "key": "latitude", "page": 1, "x": 120.0, "y": 680.0, "fontSize": 9, "font": "Helvetica", "align": "left" },
    { "key": "longitude", "page": 1, "x": 220.0, "y": 680.0, "fontSize": 9, "font": "Helvetica", "align": "left" }
  ]
}
```
3. **PDF Generator Service:** Stamped overlay renderer generates printable single/multi-page PDFs without disturbing original form lines or official water ministry stamps.

---

## 5. Security & Permission Matrix

All users operate on a single application build. Role capability flags enforce authorization:

| Capability / Function | Enumerator (عداد) | District Supervisor (مشرف مديرية) | Governorate Supervisor (مشرف محافظة) | Central Admin (مسؤول مركزي) |
| :--- | :---: | :---: | :---: | :---: |
| Conduct Field Surveys | ✅ | ✅ | ✅ | ✅ |
| View Own Surveys | ✅ | ✅ | ✅ | ✅ |
| View Assigned District Surveys | ❌ | ✅ | ✅ | ✅ |
| View Assigned Governorate Surveys | ❌ | ❌ | ✅ | ✅ |
| Submit Admin Change Requests | ✅ | ✅ | ✅ | ✅ |
| Review & Approve Change Requests | ❌ | ❌ | ✅ | ✅ |
| Soft Delete Records | ❌ | ❌ | ✅ | ✅ |
| Export Data to Excel / PDF | ✅ (Own) | ✅ (District) | ✅ (Governorate) | ✅ (All) |
| Hard Delete / DB Wipe | ❌ | ❌ | ❌ | ❌ (Forbidden) |

---

## 6. Mobile UX & Arabic RTL Design Principles
- **RTL Baseline:** Full right-to-left layout alignment for Arabic readability.
- **High Contrast UI:** Visual elements optimized for direct daylight viewing on mobile screens.
- **Large Touch Targets:** Minimum 48px buttons and input controls for comfortable field use.
- **Offline Indicator:** Constant visual badge showing offline status, local stored records count, and active GPS lock signal strength.
