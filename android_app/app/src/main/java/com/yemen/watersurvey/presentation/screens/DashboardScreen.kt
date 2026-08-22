package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yemen.watersurvey.BuildConfig
import com.yemen.watersurvey.presentation.navigation.ScreenRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigate: (ScreenRoute) -> Unit = {}
) {
    val isEnumerator = BuildConfig.APP_ROLE == "enumerator"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yemen Water Survey",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "لوحة التحكم",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isEnumerator) "Field Enumerator Application" else "Supervisor Application",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isEnumerator) {
                Button(
                    onClick = { onNavigate(ScreenRoute.NewWellSurvey) },
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = ButtonDefaults.buttonColors(containerColor = com.yemen.watersurvey.presentation.theme.Emerald600)
                ) {
                    Text("مسح بئر جديد (New Well Survey)")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(
                onClick = { onNavigate(ScreenRoute.SurveyForms) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("استمارات المسح (Survey Forms)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigate(ScreenRoute.RecordsManager) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("إدارة السجلات (Records Manager)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigate(ScreenRoute.Settings) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("الإعدادات (Settings)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigate(ScreenRoute.FormManagement) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("إدارة النماذج (Form Management)")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigate(ScreenRoute.Export) },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("التصدير (Export)")
            }

            if (isEnumerator) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(ScreenRoute.SurveySyncExport) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("تصدير المسح (.ywsync)")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(ScreenRoute.SupervisorSyncDashboard) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("مزامنة المشرف (Supervisor Sync)")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(ScreenRoute.SurveySyncImport) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("استيراد المسح (Survey Sync Import)")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(ScreenRoute.SurveyMergeReview) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("مراجعة الدمج (Merge Review)")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onNavigate(ScreenRoute.AdminReferenceManagement) },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("إدارة المرجعية (Admin Reference)")
                }
            }
        }
    }
}
