package com.yemen.watersurvey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.screens.DashboardScreen
import com.yemen.watersurvey.presentation.theme.YemenWaterSurveyTheme

/**
 * Single Activity entry point for the Yemen Water Survey application.
 * P2.5 — minimal NavHost hosting Dashboard as the start destination.
 * Full screen wiring is added in subsequent P2.5 prompts.
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
    NavHost(
        navController = navController,
        startDestination = ScreenRoute.Dashboard.route
    ) {
        composable(ScreenRoute.Dashboard.route) {
            DashboardScreen()
        }
    }
}
