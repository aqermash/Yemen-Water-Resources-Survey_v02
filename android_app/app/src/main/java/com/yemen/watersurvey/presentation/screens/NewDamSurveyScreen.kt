package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun NewDamSurveyScreen(
    surveyUUID: String? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: SurveyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(surveyUUID) {
        if (surveyUUID != null) {
            viewModel.loadRecordForEdit(surveyUUID)
        } else {
            viewModel.updateSurveyType(SurveyType.DAM)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (surveyUUID != null) "تعديل مسح سد / حاجز" else "مسح سد / حاجز مائي جديد",
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
        if (uiState.saveSuccess || uiState.draftSaveSuccess) {
            val successText = if (uiState.draftSaveSuccess) "تم حفظ مسودة السد بنجاح!" else "تم حفظ مسح السد بنجاح!"
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (uiState.draftSaveSuccess) Amber400 else Emerald400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(successText, color = Slate100, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = if (uiState.draftSaveSuccess) Amber600 else Emerald600)
                    ) {
                        Text("العودة إلى إدارة السجلات")
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
                SurveyAdminLocationBindingSection(
                    selector = viewModel.selector,
                    resolver = viewModel.resolver,
                    currentGpsLocation = uiState.gpsLocation,
                    initialAdmin1Pcode = uiState.admin1Pcode,
                    initialAdmin2Pcode = uiState.admin2Pcode,
                    initialAdmin3Pcode = uiState.admin3Pcode,
                    initialVillageRefId = uiState.villageRefId,
                    initialSnapshot = uiState.snapshot,
                    initialCustomVillageName = uiState.snapshot?.villageNameAr,
                    initialIsOverride = uiState.isLocalOverride,
                    initialOverrideReason = uiState.overrideReason,
                    onAdministrativeIdentityChanged = { a1, a2, a3, v, snap, over, reason, status, res, gps ->
                        viewModel.updateAdminLocation(a1, a2, a3, v, snap, over, reason, status, res, gps)
                    }
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "بيانات السد / الحاجز المائي",
                            color = Slate100,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.damNameAr,
                            onValueChange = { viewModel.updateDamName(it) },
                            label = { Text("اسم السد / الحاجز (بالعربية) *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.structureType,
                            onValueChange = { viewModel.updateStructureType(it) },
                            label = { Text("نوع المنشأة المائية (سد ترابي، خرساني، حاجز...) *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.storageCapacityM3,
                            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) viewModel.updateStorageCapacity(it) },
                            label = { Text("السعة التخزينية (م3)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.damHeightM,
                            onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) viewModel.updateDamHeight(it) },
                            label = { Text("ارتفاع السد / الحاجز (م)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200
                            )
                        )

                        OutlinedTextField(
                            value = uiState.structuralCondition,
                            onValueChange = { viewModel.updateStructuralCondition(it) },
                            label = { Text("الحالة الانشائية (جيدة، متوسطة، سيئة...)") },
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

                // 2.5. Photo Attachments
                PhotoAttachmentSection(
                    surveyUUID = uiState.surveyUUID,
                    recordId = uiState.recordId,
                    viewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
                    isRecordLoading = uiState.isLoadingRecord
                )

                if (uiState.error != null) {
                    Text(
                        text = uiState.error!!,
                        color = Red400,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // 3. Save Actions: Draft & Final
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save as Draft (No GPS gate, incomplete allowed)
                    OutlinedButton(
                        onClick = { viewModel.saveAsDraft() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !uiState.isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Amber400))
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Amber400)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ كمسودة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Complete / Save Final (Requires GPS < 15m and required fields)
                    Button(
                        onClick = { viewModel.saveSurvey() },
                        modifier = Modifier.weight(1.3f).height(52.dp),
                        enabled = !uiState.isSaving && uiState.gpsLocation != null && uiState.gpsLocation!!.accuracyM < 15.0f,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald600,
                            disabledContainerColor = Slate800
                        )
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Slate100)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إكمال وحفظ المسح", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
