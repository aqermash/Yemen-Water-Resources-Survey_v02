package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yemen.watersurvey.domain.model.SurveyType
import com.yemen.watersurvey.presentation.viewmodel.SurveyViewModel
import com.yemen.watersurvey.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyFormsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToNewWell: () -> Unit = {},
    viewModel: SurveyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إضافة مسح جديد",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate100,
                    navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { paddingValues ->
        if (uiState.saveSuccess) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("تم حفظ المسح بنجاح!", color = Slate100, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateBack) {
                        Text("العودة للوحة التحكم")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateToNewWell,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مسح بئر جديد (New Well Survey)", fontWeight = FontWeight.Bold)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("نوع المنشأة المائية", color = Slate300, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SurveyType.values().forEach { type ->
                                FilterChip(
                                    selected = uiState.surveyType == type,
                                    onClick = { viewModel.updateSurveyType(type) },
                                    label = { Text(type.displayNameAr) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Emerald900,
                                        selectedLabelColor = Emerald400,
                                        labelColor = Slate400
                                    )
                                )
                            }
                        }
                    }
                }

                SurveyAdminLocationBindingSection(
                    selector = viewModel.selector,
                    resolver = viewModel.resolver,
                    currentGpsLocation = uiState.gpsLocation,
                    onAdministrativeIdentityChanged = { a1, a2, a3, v, snap, over, reason, status, res, gps ->
                        viewModel.updateAdminLocation(a1, a2, a3, v, snap, over, reason, status, res, gps)
                    }
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = Red400,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.saveSurvey() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !uiState.isSaving && uiState.gpsLocation != null && uiState.gpsLocation!!.accuracyM < 15.0f,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald600,
                        disabledContainerColor = Slate800
                    )
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Slate100)
                    } else {
                        Text("حفظ المسح الميداني", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
