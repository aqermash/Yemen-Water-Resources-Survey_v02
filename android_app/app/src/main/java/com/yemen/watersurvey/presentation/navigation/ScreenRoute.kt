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
}
