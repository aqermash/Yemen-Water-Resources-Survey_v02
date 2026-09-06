package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yemen.watersurvey.core.expression.DefaultFormExpressionEvaluator
import com.yemen.watersurvey.core.expression.FormExpressionEvaluator
import com.yemen.watersurvey.domain.model.FormPackage
import com.yemen.watersurvey.domain.model.RuntimeValue
import com.yemen.watersurvey.domain.model.SurveyType
import com.yemen.watersurvey.presentation.form.*
import com.yemen.watersurvey.presentation.theme.*
import com.yemen.watersurvey.presentation.viewmodel.SurveyViewModel

/**
 * Stateful Dynamic Survey Screen connected to SurveyViewModel and the active Form Package.
 *
 * Implements Phase 14 Runtime Integration:
 * - Dynamically resolves active Form Package for WELL, SPRING, DAM / WATER_HARVESTING
 * - Preserves historical form identity for existing surveys
 * - Wires DynamicFormRenderer, DynamicFormState, ExpressionEngine, GPS, Media, and Room persistence
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicSurveyScreen(
    surveyType: SurveyType? = null,
    surveyUUID: String? = null,
    onNavigateBack: () -> Unit = {},
    viewModel: SurveyViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(surveyType, surveyUUID) {
        if (surveyUUID != null) {
            viewModel.loadRecordForEdit(surveyUUID)
        } else if (surveyType != null) {
            viewModel.initializeDynamicSurvey(surveyType)
        }
    }

    if (uiState.saveSuccess || uiState.draftSaveSuccess) {
        val successText = if (uiState.draftSaveSuccess) "تم حفظ مسودة الاستمارة بنجاح!" else "تم حفظ استمارة المسح الميداني بنجاح!"
        Scaffold(
            containerColor = Slate950
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
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
        }
    } else if (uiState.isLoadingRecord) {
        Scaffold(containerColor = Slate950) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Emerald500)
            }
        }
    } else if (uiState.activeFormPackage == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("استمارة المسح الميداني", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = uiState.error ?: "لا توجد حزمة استمارة نشطة معتمدة لهذا المسح.",
                        color = Red400,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                    ) {
                        Text("رجوع")
                    }
                }
            }
        }
    } else {
        DynamicSurveyScreen(
            formPackage = uiState.activeFormPackage!!,
            formState = uiState.dynamicFormState,
            onValueChanged = { field, value -> viewModel.onDynamicFieldValueChanged(field, value) },
            onSaveDraft = { viewModel.saveAsDraft() },
            onSaveFinal = { viewModel.saveSurvey() },
            onNavigateBack = onNavigateBack,
            expressionEvaluator = viewModel.expressionEvaluator,
            adminLookupProvider = viewModel.adminLookupProvider,
            onCaptureGps = { fieldName -> viewModel.onCaptureGpsForDynamicField(fieldName) },
            onCaptureImage = { fieldName ->
                viewModel.onCaptureImageForDynamicField(fieldName, "ATT-${System.currentTimeMillis()}")
            },
            isSaving = uiState.isSaving,
            errorMessage = uiState.error
        )
    }
}

/**
 * Pure Stateless Dynamic Survey Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicSurveyScreen(
    formPackage: FormPackage,
    formState: DynamicFormState,
    onValueChanged: (fieldName: String, value: RuntimeValue?) -> Unit,
    onSaveDraft: () -> Unit,
    onSaveFinal: () -> Unit,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    expressionEvaluator: FormExpressionEvaluator = DefaultFormExpressionEvaluator,
    adminLookupProvider: AdminLookupProvider? = null,
    onCaptureGps: ((fieldName: String) -> Unit)? = null,
    onCaptureImage: ((fieldName: String) -> Unit)? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    val scrollState = rememberScrollState()
    val formDef = formPackage.formDefinition

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = formPackage.name.ifBlank { formDef?.title ?: "استمارة مسح ميداني" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
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
        if (formDef == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "تعريف الاستمارة غير متوفر في هذه الحزمة.",
                    color = Red400,
                    fontSize = 14.sp
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
            ) {
                // Dynamic Questionnaire Tree
                DynamicFormRenderer(
                    formDefinition = formDef,
                    choiceLists = formPackage.choiceLists,
                    formState = formState,
                    onValueChanged = onValueChanged,
                    expressionEvaluator = expressionEvaluator,
                    adminLookupProvider = adminLookupProvider,
                    onCaptureGps = onCaptureGps,
                    onCaptureImage = onCaptureImage,
                    isReadOnly = formState.isReadOnly
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = Red400,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }

                // Action Buttons: Save Draft & Complete Final
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveDraft,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        enabled = !isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Amber400))
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Amber400)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ كمسودة", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onSaveFinal,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp),
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                    ) {
                        if (isSaving) {
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
