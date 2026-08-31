# Phase 12: JSON-Based Dynamic Form Engine

## Objective
Implement a metadata-driven dynamic form engine that compiles and renders user interface components at runtime from JSON form definitions (`form_definition.json` and `choices.json`). This moves the application from hardcoded screens to a flexible, updateable form framework.

## Current Project Context
The application contains a `FormPackageManager` that reads and validates versioned ZIP packages containing `form_definition.json` and `choices.json` files. However, the survey UI is statically implemented in individual screen files. The dynamic form engine will interpret the validated JSON structure and display the appropriate UI controls dynamically.

## Existing Files To Inspect
- **Package Management:** [`android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageManager.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/core/form/FormPackageManager.kt)
- **JSON Definitions:** Sample assets / files created during form package import.
- **Compose Renderer Seam:** Locate/create the entry point in [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyFormsScreen.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/SurveyFormsScreen.kt) to replace hardcoded screen launches.

## Requirements
1. **JSON Form Parser:** 
   - Parse standard XLSForm-inspired JSON schemas detailing question keys, types (`text`, `integer`, `decimal`, `select_one`, `select_multiple`, `date`), labels (Arabic/English), relevancy rules, and constraints.
2. **Dynamic Compose Renderer:**
   - Develop Compose components mapped to form elements:
     - `text` $\rightarrow$ `OutlinedTextField`
     - `select_one` $\rightarrow$ Dropdown / Radio group
     - `select_multiple` $\rightarrow$ Checkboxes
     - `group` $\rightarrow$ Collapsible card / section
3. **Validation Rules:**
   - Process validation constraints declared in the JSON (e.g., `required`, ranges, character limits).
4. **Conditional Visibility (Relevancy):**
   - Show/hide fields reactively based on values entered in previous fields (e.g., show "pump details" only if "operational status" is "active").

## Implementation Steps
1. **Model Definitions:** Define Kotlin classes mapping the elements in `form_definition.json` (e.g., `FormElement`, `Question`, `Choice`).
2. **Parser Component:** Write a parser that transforms raw JSON text into structured Kotlin models.
3. **Dynamic Composables:** Create a set of reusable Composable controls that take question models and value state bindings.
4. **Layout Builder:** Write a container composable that lists and displays elements sequentially.
5. **Wired UI Integration:** Connect the renderer inside a dynamic survey runner screen.

## Constraints / Do Not Change
- **Do NOT break native GPS & administrative bindings.** Cascading admin selection and PIP verification must remain native, high-performance features.
- **Do NOT introduce web views or JS engines** (like React Native, Cordova, or standard WebView Leaflet) to render forms. The engine must remain 100% native Jetpack Compose.

## Testing Requirements
- **Unit Tests:** Parse various sample form definition files and check the integrity of the object model mapping.
- **Integration Tests:** Verify that dynamically loaded layouts render inside Robolectric test environments.
- **UI Tests:** Simulate interactions on a dynamic screen (typing, selecting, verifying visibility state changes).

## Final Report Requirements
Compile a walkthrough showing:
- Rendering of custom imported form definitions.
- Speed/latency measurements for form layout compilation.
- Proof of field visibility changes on interactive checks.
