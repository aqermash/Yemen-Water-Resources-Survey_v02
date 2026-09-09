package com.yemen.watersurvey.presentation.navigation

sealed class ScreenRoute(val route: String) {
    object PinLock : ScreenRoute("pin_lock")
    object Dashboard : ScreenRoute("dashboard")
    object SurveyForms : ScreenRoute("survey_forms")
    object FormManagement : ScreenRoute("form_management")
    object RecordsManager : ScreenRoute("records_manager")
    object Export : ScreenRoute("export")
    object SurveySyncExport : ScreenRoute("survey_sync_export")
    object SurveySyncImport : ScreenRoute("survey_sync_import")
    object SurveyMergeReview : ScreenRoute("survey_merge_review")
    object SupervisorSyncDashboard : ScreenRoute("supervisor_sync_dashboard")
    object AdminReferenceManagement : ScreenRoute("admin_reference_management")
    object Settings : ScreenRoute("settings")
    object NewWellSurvey : ScreenRoute("new_well_survey")
    object NewSpringSurvey : ScreenRoute("new_spring_survey")
    object NewDamSurvey : ScreenRoute("new_dam_survey")
    // Phase 10C — Preview and Edit with UUID argument
    object SurveyPreview : ScreenRoute("survey_preview/{surveyUUID}") {
        fun createRoute(uuid: String) = "survey_preview/$uuid"
    }
    object EditWellSurvey : ScreenRoute("edit_well_survey/{surveyUUID}") {
        fun createRoute(uuid: String) = "edit_well_survey/$uuid"
    }
    object EditSpringSurvey : ScreenRoute("edit_spring_survey/{surveyUUID}") {
        fun createRoute(uuid: String) = "edit_spring_survey/$uuid"
    }
    object EditDamSurvey : ScreenRoute("edit_dam_survey/{surveyUUID}") {
        fun createRoute(uuid: String) = "edit_dam_survey/$uuid"
    }
    object FieldPackageManager : ScreenRoute("field_package_manager")
}
