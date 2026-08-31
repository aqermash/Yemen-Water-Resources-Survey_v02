# Phase 11: Draft Lifecycle, Survey Editing & Reopening

## Objective
Implement survey draft persistence, survey editing/reopening, and local revision tracking. This allows field enumerators to save incomplete surveys as drafts, resume editing later, and update completed records with detailed revision history while maintaining database integrity.

## Current Project Context
The database currently defines `SurveyRevisionEntity` to hold historic snapshots. In the presentation layer, `RecordsManagerScreen` displays a read-only list of surveys. All surveys are saved with `workflowStatus = "COMPLETED"` and cannot be edited. A draft workflow needs to be integrated, allowing records to exist in `DRAFT` status and be reloaded into the editor.

## Existing Files To Inspect
- **Room Entities:** 
  - [`android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRecordEntity.kt) (inspect `workflowStatus` and `revisionCount`)
  - [`android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRevisionEntity.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/entity/SurveyRevisionEntity.kt)
- **Room DAOs:**
  - [`android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyRecordDao.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyRecordDao.kt)
  - [`android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyRevisionDao.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/data/dao/SurveyRevisionDao.kt)
- **ViewModels:** [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/viewmodel/SurveyViewModel.kt)
- **UI Screens:**
  - [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/RecordsManagerScreen.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/RecordsManagerScreen.kt)
  - [`android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/NewWellSurveyScreen.kt`](file:///d:/Dev/Project%20Yemen%20Water%20Survey_v02/android_app/app/src/main/java/com/yemen/watersurvey/presentation/screens/NewWellSurveyScreen.kt) (similar for Spring and Dam screens)

## Requirements
1. **DRAFT Status Integration:** 
   - Add a "Save Draft" button to all survey screens.
   - Set the `workflowStatus` of drafts to `DRAFT`.
   - GPS accuracy gate (< 15 m) is bypassed for drafts (but mandatory for final save).
2. **Resume Editing Workflow:**
   - In `RecordsManagerScreen`, make items interactive.
   - When a draft item is clicked, load its fields back into the corresponding survey screen and update the active ViewModel state.
3. **Local Revisions:**
   - When an already `COMPLETED` survey is reopened and saved again, increment `revisionCount` and write a new snapshot into `SurveyRevisionEntity`.
   - Document change reasons in the revision table.
4. **Workflow Preservation:**
   - Ensure that transitioning states (`DRAFT` $\rightarrow$ `COMPLETED`) is strictly guided by business rules.
   - Synced/imported records should retain supervisor review flags and lock state to prevent accidental changes.

## Implementation Steps
1. **ViewModel Expansion:** Add functions in `SurveyViewModel` to populate UI state from an existing `SurveyRecordEntity` (reopening logic).
2. **"Save Draft" Option:** Implement `saveSurveyAsDraft()` in `SurveyViewModel` which saves records without enforcing required-field validations and GPS accuracy gates.
3. **Navigation Integration:** Update Compose navigation in `MainActivity` to accept a `surveyUUID` parameter for survey screens so that they can load in "edit mode".
4. **Records Manager Interactive State:** Add action buttons (Edit/Resume) to cards in the records list.
5. **Revision Recording:** Implement a transaction that logs a row in `SurveyRevisionEntity` before updating `SurveyRecordEntity` with changes.

## Constraints / Do Not Change
- **Do NOT delete historical revisions.** The revisions database must remain immutable.
- **Do NOT allow saving a finalized survey (COMPLETED)** if it does not satisfy the < 15 m GPS accuracy gate.

## Testing Requirements
- **Unit Tests:** Verify that editing a survey correctly increments `revisionCount` and generates a valid `SurveyRevisionEntity`.
- **UI Tests:** Simulate loading a draft survey, editing fields, and verifying that the UI correctly displays the updated values.

## Final Report Requirements
Compile a walkthrough showing:
- Verification of workflow transitions (`DRAFT` $\rightarrow$ `COMPLETED`).
- Screenshots of the draft resume button and the updated Records Manager UI.
