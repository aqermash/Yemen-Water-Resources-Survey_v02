package com.yemen.watersurvey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.screens.DashboardScreen
import com.yemen.watersurvey.presentation.screens.DynamicSurveyScreen
import com.yemen.watersurvey.presentation.screens.ExportScreen
import com.yemen.watersurvey.presentation.screens.FormManagementScreen
import com.yemen.watersurvey.presentation.screens.NewWellSurveyScreen
import com.yemen.watersurvey.presentation.screens.NewSpringSurveyScreen
import com.yemen.watersurvey.presentation.screens.NewDamSurveyScreen
import com.yemen.watersurvey.presentation.screens.PinLockScreen
import com.yemen.watersurvey.presentation.screens.RecordsManagerScreen
import com.yemen.watersurvey.presentation.screens.SurveyPreviewScreen
import com.yemen.watersurvey.presentation.screens.AdminReferenceManagementScreen
import com.yemen.watersurvey.presentation.screens.SettingsScreen
import com.yemen.watersurvey.presentation.screens.SupervisorSyncDashboardScreen
import com.yemen.watersurvey.presentation.screens.SurveyFormsScreen
import com.yemen.watersurvey.presentation.screens.SurveyMergeReviewScreen
import com.yemen.watersurvey.presentation.screens.SurveySyncExportScreen
import com.yemen.watersurvey.presentation.screens.SurveySyncImportScreen
import com.yemen.watersurvey.presentation.theme.YemenWaterSurveyTheme

/**
 * Single Activity entry point for the Yemen Water Survey application.
 * P2.5 — NavHost hosting Dashboard, SurveyForms, RecordsManager, and Settings.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YemenWaterSurveyTheme {
                YemenWaterSurveyNavHost()
            }
        }
    }
}

@Composable
private fun YemenWaterSurveyNavHost() {
    val navController = rememberNavController()
    val isSupervisor = BuildConfig.APP_ROLE == "supervisor"
    val startDestination = if (isSupervisor) ScreenRoute.PinLock.route else ScreenRoute.Dashboard.route
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(ScreenRoute.PinLock.route) {
            PinLockScreen(
                onPinVerified = {
                    navController.navigate(ScreenRoute.Dashboard.route) {
                        popUpTo(ScreenRoute.PinLock.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ScreenRoute.Dashboard.route) {
            DashboardScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.SurveyForms.route) {
            SurveyFormsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToNewWell = {
                    navController.navigate(ScreenRoute.NewWellSurvey.route)
                },
                onNavigateToNewSpring = {
                    navController.navigate(ScreenRoute.NewSpringSurvey.route)
                },
                onNavigateToNewDam = {
                    navController.navigate(ScreenRoute.NewDamSurvey.route)
                }
            )
        }
        composable(ScreenRoute.RecordsManager.route) {
            RecordsManagerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPreview = { surveyUUID ->
                    navController.navigate(ScreenRoute.SurveyPreview.createRoute(surveyUUID))
                },
                onNavigateToEdit = { surveyType, surveyUUID ->
                    val route = when (surveyType) {
                        "WELL" -> ScreenRoute.EditWellSurvey.createRoute(surveyUUID)
                        "SPRING" -> ScreenRoute.EditSpringSurvey.createRoute(surveyUUID)
                        "DAM" -> ScreenRoute.EditDamSurvey.createRoute(surveyUUID)
                        else -> ScreenRoute.EditWellSurvey.createRoute(surveyUUID)
                    }
                    navController.navigate(route)
                }
            )
        }
        composable(
            route = ScreenRoute.SurveyPreview.route,
            arguments = listOf(navArgument("surveyUUID") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyUUID = backStackEntry.arguments?.getString("surveyUUID") ?: ""
            SurveyPreviewScreen(
                surveyUUID = surveyUUID,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = ScreenRoute.EditWellSurvey.route,
            arguments = listOf(navArgument("surveyUUID") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyUUID = backStackEntry.arguments?.getString("surveyUUID") ?: ""
            DynamicSurveyScreen(
                surveyUUID = surveyUUID,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = ScreenRoute.EditSpringSurvey.route,
            arguments = listOf(navArgument("surveyUUID") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyUUID = backStackEntry.arguments?.getString("surveyUUID") ?: ""
            DynamicSurveyScreen(
                surveyUUID = surveyUUID,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = ScreenRoute.EditDamSurvey.route,
            arguments = listOf(navArgument("surveyUUID") { type = NavType.StringType })
        ) { backStackEntry ->
            val surveyUUID = backStackEntry.arguments?.getString("surveyUUID") ?: ""
            DynamicSurveyScreen(
                surveyUUID = surveyUUID,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(ScreenRoute.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(ScreenRoute.FormManagement.route) {
            FormManagementScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.Export.route) {
            ExportScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.SupervisorSyncDashboard.route) {
            SupervisorSyncDashboardScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.SurveySyncExport.route) {
            SurveySyncExportScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.SurveySyncImport.route) {
            SurveySyncImportScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.SurveyMergeReview.route) {
            SurveyMergeReviewScreen(
                onNavigate = {
                    navController.navigate(it.route)
                }
            )
        }
        composable(ScreenRoute.AdminReferenceManagement.route) {
            AdminReferenceManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(ScreenRoute.NewWellSurvey.route) {
            DynamicSurveyScreen(
                surveyType = com.yemen.watersurvey.domain.model.SurveyType.WELL,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(ScreenRoute.NewSpringSurvey.route) {
            DynamicSurveyScreen(
                surveyType = com.yemen.watersurvey.domain.model.SurveyType.SPRING,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(ScreenRoute.NewDamSurvey.route) {
            DynamicSurveyScreen(
                surveyType = com.yemen.watersurvey.domain.model.SurveyType.DAM,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

