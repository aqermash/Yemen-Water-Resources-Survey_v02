# Yemen Water Survey — XLSForm & Form Package Contract

**Document Version:** 1.0.0  
**Status:** ACTIVE / SPECIFICATION  
**Scope:** Form Package Parsing, Dynamic Question Rendering, and Expression Evaluation  
**Target Platform:** Yemen Water Survey Mobile App (Native Jetpack Compose Engine)

---

## 1. Architectural Overview & Distribution Packaging

The Yemen Water Survey application interprets dynamic survey questionnaires via a decoupled, offline-first **Form Package** architecture (Phase 12A/12B/13/14). Rather than running heavy webviews, ODK JavaRosa interpreters, or compiling raw binary `.xlsx` workbooks on the mobile device at runtime, XLSForms are pre-packaged into structured ZIP archives (`.zip` / staged directories) containing verified JSON schemas.

### Required Package Structure
```text
form_package_zip/
├── metadata.json          [MANDATORY] Package identity, survey type, min app version
├── form_definition.json   [MANDATORY] Questionnaire hierarchy, elements, rules
├── choices.json           [MANDATORY] Choice lists & external options
├── sequence_pool.json     [OPTIONAL]  Preallocated offline registry sequence ranges
├── official_template.pdf  [OPTIONAL]  Official Ministry stamp/certificate template
└── pdf_mapping.json       [OPTIONAL]  Coordinate mapping for PDF certificate generation
```

---

## 2. Supported XLSForm Worksheet Equivalents

| Standard XLSForm Sheet | Form Package File Equivalent | Parsed Domain Model | Parser Class & Line | Status |
| :--- | :--- | :--- | :--- | :---: |
| `survey` | `form_definition.json` | `FormDefinition`, `FormElement` | `FormDefinitionParser.kt:177` | **CURRENT** |
| `choices` | `choices.json` | `Map<String, ChoiceList>` | `FormDefinitionParser.kt:115` | **CURRENT** |
| `settings` | `metadata.json` | `PackageMetadata` | `FormDefinitionParser.kt:53` | **CURRENT** |
| `entities` / `external_choices` | Dynamic lookup / Room DB | `AdminLookupProvider` | `RoomAdminLookupProvider.kt` | **CURRENT** |

---

## 3. Supported Question & Element Types

The dynamic form engine (`FormDefinitionParser.kt`, `DynamicFormRenderer.kt`) parses and natively renders the following question types:

| XLSForm Type Keyword | Internal Data Type | Rendered Compose Component | Description / Input Behavior | Status |
| :--- | :--- | :--- | :--- | :---: |
| `text` / `string` | `QuestionDataType.TEXT` | `OutlinedTextField` | Single or multi-line text entry with Arabic RTL support. | **CURRENT** |
| `integer` / `int` | `QuestionDataType.INTEGER` | `OutlinedTextField(KeyboardType.Number)` | Whole numbers (e.g. well depth, household counts). | **CURRENT** |
| `decimal` / `double` | `QuestionDataType.DECIMAL` | `OutlinedTextField(KeyboardType.Decimal)` | Floating point numbers (e.g. discharge LPS, capacity m³). | **CURRENT** |
| `date` | `QuestionDataType.DATE` | Date Picker Modal / Field | ISO-8601 date string (`YYYY-MM-DD`). | **CURRENT** |
| `select_one [list]` | `QuestionDataType.SELECT_ONE` | Radio Button List / Exposed Dropdown | Single selection from declared choice list. | **CURRENT** |
| `select_multiple [list]`| `QuestionDataType.SELECT_MULTIPLE` | Checkbox Group / Multi-Select Card | Multiple selection from declared choice list. | **CURRENT** |
| `select_one_from_file` / `admin_select` | `QuestionDataType.ADMIN_SELECT` | `AdminCascadingDropdown` | P-code spatial reference selector linked to Governorates, Districts, Uzlahs, Villages. | **CURRENT** |
| `geopoint` / `location` | `QuestionDataType.GEOPOINT` | `GpsCaptureControl` | Native GPS acquisition with `<15m` accuracy gate. | **CURRENT** |
| `image` / `photo` | `QuestionDataType.IMAGE` | `CameraCaptureWidget` | CameraX photo capture with SHA-256 hash stamp. | **CURRENT** |
| `begin_group` / `end_group` | `GroupElement` | `ExpandableSectionCard` | Collapsible visual container grouping child elements. | **CURRENT** |
| `calculate` | `CalculateElement` | *Non-visual in-memory evaluator* | Evaluated dynamically; saved to database payload. | **CURRENT** |
| `hidden` | `HiddenElement` | *Non-visual parameter* | Static or default configuration parameters. | **CURRENT** |
| `start` / `end` | `SystemTimestampElement` | *Non-visual audit capture* | Session start and completion timestamps. | **CURRENT** |
| `note` | `QuestionDataType.NOTE` | Text Display / Alert Box | Read-only guidance note with no user input. | **PLANNED** |
| `begin_repeat` / `end_repeat` | `RepeatGroupElement` | Sub-table / Dynamic List | Repeating questionnaire items (e.g. water test rounds). | **PLANNED** |

---

## 4. Supported Expression Engine Syntax & Grammar

Expressions in `relevant`, `constraint`, `calculation`, and `choice_filter` are parsed by a Recursive-Descent AST parser (`ExpressionParser.kt`) and evaluated offline without webviews or external interpreters (`ExpressionEvaluatorEngine.kt`).

### 4.1 Operator Precedence Table

| Level | Category | Operators | Syntax Example | Evaluation Behavior |
| :---: | :--- | :--- | :--- | :--- |
| **1** | Logical OR | `or` | `${status} = 'active' or ${override} = 'yes'` | Short-circuit boolean evaluation |
| **2** | Logical AND | `and` | `${depth} > 50 and ${yield} > 10` | Short-circuit boolean evaluation |
| **3** | Equality & Comparison | `=`, `!=`, `>`, `>=`, `<=` | `${type} != 'dry'` | Numeric or lexicographic comparison |
| **4** | Multiplicative | `*`, `div` | `${flow_lps} * 3.6` | Multiplication and division (safe div by 0) |
| **5** | Primary & Atom | `${field}`, `.`, literals, `()` | `${name}`, `100`, `'val'`, `(a or b)` | Scope variable lookup, string/number literals |

### 4.2 Built-in Function Library

| Function Signature | Arguments | Description | Example Usage | Status |
| :--- | :--- | :--- | :--- | :---: |
| `if(condition, then, else)` | 3 | Ternary conditional branch | `if(${depth} > 100, 'DEEP', 'SHALLOW')` | **CURRENT** |
| `concat(arg1, arg2, ...)` | Variadic | Concatenates string representations | `concat(${gov_code}, '-', ${well_type})` | **CURRENT** |
| `uuid()` | 0 | Generates or returns active survey UUID | `uuid()` | **CURRENT** |
| `today()` | 0 | Returns current date in `YYYY-MM-DD` | `today()` | **CURRENT** |
| `selected(field, 'val')` | 2 | Checks if option `val` is chosen in multi/single choice | `selected(${uses}, 'drinking')` | **CURRENT** |
| `count-selected(field)` | 1 | Returns number of checked options | `count-selected(${uses}) > 1` | **CURRENT** |
| `not(condition)` | 1 | Negates boolean expression | `not(selected(${status}, 'closed'))` | **PLANNED** |
| `regex(field, pattern)` | 2 | Evaluates regular expression constraint | `regex(., '^[0-9]{4}$')` | **PLANNED** |

### 4.3 Explicitly Unsupported Expression Operators & Constructs

To guarantee deterministic, sandboxed execution on resource-constrained Android devices, the following operators are **explicitly rejected** by `Tokenizer.kt:163` and `ExpressionParser.kt:36`:
- Standalone `<` (Must use `<=` or rearrange with `>`)
- Plus sign `+` (Arithmetic addition not in operator table; `concat()` used for strings)
- Slash `/` (Must use `div` keyword for division)
- Infix Subtraction `-` (Hyphen only accepted as negative literal number prefix or string token)

---

## 5. Choice Lists & Administrative Cascades

### 5.1 Static Choice Lists (`choices.json`)
Choice lists are defined as JSON arrays or key-value object dictionaries:
```json
{
  "operational_status_list": [
    { "name": "functional", "labelAr": "يعمل بشكل جيد", "labelEn": "Functional", "sortOrder": 1 },
    { "name": "partially_functional", "labelAr": "يعمل جزئياً / بحاجة صيانة", "labelEn": "Partially Functional", "sortOrder": 2 },
    { "name": "non_functional", "labelAr": "متوقف تماماً عن العمل", "labelEn": "Non-Functional", "sortOrder": 3 }
  ]
}
```

### 5.2 Dynamic Cascading Choice Filters (`choice_filter`)
Filtered choices evaluate predicates dynamically against item properties:
```xlsform
type: select_one uzlah_list
name: uzlah_code
choice_filter: district_code = ${district_code}
```

---

## 6. Reserved Question Names & Structural Rules

### 6.1 Prohibited / Reserved Question Names
Questionnaire authors MUST NOT name survey questions using system-managed metadata keywords, as doing so would collide with Room database columns and serialization envelopes:

| Reserved Identifier | Reason for Prohibition |
| :--- | :--- |
| `survey_uuid`, `surveyUUID` | Primary global record key |
| `record_id`, `recordId` | Local tracking ID |
| `registry_code`, `registryCode` | Official national GIS registry code |
| `enumerator_code`, `enumerator_id`, `enumerator_username` | Identity session tokens |
| `form_id`, `form_version`, `schema_version` | Form package version descriptors |
| `workflow_status`, `revision_count` | Lifecycle and revision tracking |
| `created_at`, `updated_at`, `submitted_at` | System audit timestamps |
| `admin1_pcode`, `admin2_pcode`, `admin3_pcode`, `village_ref_id` | National administrative spatial hierarchy |
| `latitude`, `longitude`, `altitude_m`, `accuracy_m`, `gps_*` | Geospatial fix data |
| `wellDetailsJson`, `springDetailsJson`, `damDetailsJson` | Type-specific serialization blobs |

### 6.2 Structural Validation & Directed Acyclic Graph (DAG) Integrity
The form package validator (`FormPackageValidator.kt`) enforces:
1. **Uniqueness:** All element names across root and child groups must be unique.
2. **Referential Integrity:** Every `select_one` / `select_multiple` question must map to a declared list in `choices.json`.
3. **Cycle Detection (DFS 3-Color):** All inter-field calculations and relevance expressions must form a Directed Acyclic Graph (DAG). Circular dependencies (e.g. `A depends on B, B depends on A`) are rejected immediately with `INVALID_PACKAGE_STRUCTURE`.

---

## 7. Target XLSForm Gap Summary & Planned Additions

| Target Feature | Current Parser Status | Severity | Alignment Plan |
| :--- | :--- | :---: | :--- |
| `note` question type | Omitted / Treated as Text | B | Add explicit `QuestionDataType.NOTE` non-editable display composable. |
| `begin_repeat` / `end_repeat` | Unsupported | B | Implement `RepeatGroupElement` and sub-table list persistence. |
| Arithmetic `+`, `-` operators | Rejected by Tokenizer | B | Add `+` and infix `-` to `Tokenizer.kt` and `ExpressionParser.kt`. |
| Standalone `<` operator | Rejected by Tokenizer | B | Add `<` token recognition without colliding with XML/HTML tags. |
| `not()` expression function | Not in verified function list | B | Add `not` evaluator in `ExpressionEvaluatorEngine.kt`. |
| Reserved Question Names Gate | Validator does not check | B | Add explicit reserved keyword blacklist check in `FormPackageValidator.kt`. |
