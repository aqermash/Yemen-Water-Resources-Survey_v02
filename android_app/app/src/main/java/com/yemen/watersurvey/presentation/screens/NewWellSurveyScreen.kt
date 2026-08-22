package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yemen.watersurvey.domain.model.SurveyType
import com.yemen.watersurvey.presentation.theme.*
import com.yemen.watersurvey.presentation.viewmodel.SurveyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWellSurveyScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: SurveyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مسح بئر مياه جديد",
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
                    Text("تم حفظ مسح البئر بنجاح!", color = Slate100, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                    ) {
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
                // 1. Administrative Location & GPS
                SurveyAdminLocationBindingSection(
                    selector = viewModel.selector,
                    resolver = viewModel.resolver,
                    currentGpsLocation = uiState.gpsLocation,
                    onAdministrativeIdentityChanged = { a1, a2, a3, v, snap, over, reason, status, res, gps ->
                        viewModel.updateAdminLocation(a1, a2, a3, v, snap, over, reason, status, res, gps)
                    }
                )

                // 2. Well Specific Details
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "بيانات البئر الفنية",
                            color = Slate100,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        OutlinedTextField(
                            value = uiState.wellNameAr,
                            onValueChange = { viewModel.updateWellName(it) },
                            label = { Text("اسم البئر (بالعربية) *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.wellType,
                            onValueChange = { viewModel.updateWellType(it) },
                            label = { Text("نوع البئر (يدوي، ارتوازي...) *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.wellDepthM,
                            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) viewModel.updateWellDepth(it) },
                            label = { Text("عمق البئر (متر) *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.pumpingMechanism,
                            onValueChange = { viewModel.updatePumpingMechanism(it) },
                            label = { Text("آلية الضخ (مضخة شمسية، ديزل...)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.operationalStatus,
                            onValueChange = { viewModel.updateOperationalStatus(it) },
                            label = { Text("الحالة التشغيلية (يعمل، معطل...)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )
                    }
                }

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = Red400,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // 3. Save Action
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
                        Text("حفظ مسح البئر الميداني", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
