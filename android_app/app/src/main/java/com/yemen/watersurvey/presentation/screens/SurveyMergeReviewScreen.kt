package com.yemen.watersurvey.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.sync.ConflictDetectionEngine
import com.yemen.watersurvey.core.sync.ControlledMergeExecutor
import com.yemen.watersurvey.core.sync.SurveySyncImporter
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 9.2: Conflict Detection & Controlled Record Merge Review Screen.
 *
 * Provides an explicit, auditable, offline reconciliation workflow:
 * 1. Categorizes incoming records as NEW_RECORD, UPDATE_AVAILABLE, DUPLICATE, or CONFLICT.
 * 2. Visualizes field-level difference comparisons.
 * 3. Requires explicit supervisor confirmation before any database mutations.
 * 4. Generates immutable SurveyRevision snapshots before overwriting existing data.
 * 5. Generates immutable AuditLog records for every accepted merge decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMergeReviewScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val conflictEngine = remember { ConflictDetectionEngine() }
    val mergeExecutor = remember { ControlledMergeExecutor(context, database) }
    val importer = remember { SurveySyncImporter(context) }

    var availablePackages by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPackageFile by remember { mutableStateOf<File?>(null) }
    var importPreview by remember { mutableStateOf<SyncImportPreview?>(null) }
    var mergeItems by remember { mutableStateOf<List<SurveyMergeItem>>(emptyList()) }
    var mergeSummary by remember { mutableStateOf<SurveyMergeSummary?>(null) }
    var selectedTab by remember { mutableStateOf("ALL") }
    var isLoading by remember { mutableStateOf(false) }
    var isExecutingMerge by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var mergeResult by remember { mutableStateOf<ControlledMergeExecutionResult?>(null) }
    var supervisorReasonInput by remember { mutableStateOf("اعتماد ومراجعة ميدانية مشرفة") }

    // Load available staged/sync packages
    fun loadPackages() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val files = mutableListOf<File>()
                val stagedDir = importer.acceptedStagedDir
                if (stagedDir.exists()) {
                    files.addAll(stagedDir.listFiles { f -> f.extension == "ywsync" || f.extension == "ysync" } ?: emptyArray())
                }
                val exportDir = File(context.filesDir, "sync_exports")
                if (exportDir.exists()) {
                    files.addAll(exportDir.listFiles { f -> f.extension == "ywsync" || f.extension == "ysync" } ?: emptyArray())
                }
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    files.addAll(cacheDir.listFiles { f -> f.extension == "ywsync" || f.extension == "ysync" } ?: emptyArray())
                }
                val distinctFiles = files.distinctBy { it.name }
                withContext(Dispatchers.Main) {
                    availablePackages = distinctFiles
                    if (selectedPackageFile == null && distinctFiles.isNotEmpty()) {
                        selectedPackageFile = distinctFiles.first()
                    }
                }
            }
        }
    }

    // Inspect package and analyze conflicts against Room database
    fun analyzePackage(pkgFile: File) {
        isLoading = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                // Fetch local records from Room
                val localEntities = database.surveyRecordDao().getAllSurveysSync()
                val localSurveys = localEntities.map { entity ->
                    SurveyRecord(
                        recordId = entity.recordId,
                        surveyUUID = entity.surveyUUID,
                        formId = entity.formId,
                        formVersion = entity.formVersion,
                        surveyType = try { SurveyType.valueOf(entity.surveyType) } catch (e: Exception) { SurveyType.WELL },
                        admin1Pcode = entity.admin1Pcode,
                        admin2Pcode = entity.admin2Pcode,
                        admin3Pcode = entity.admin3Pcode,
                        villageReferenceId = entity.villageCode,
                        enumeratorId = entity.enumeratorId,
                        enumeratorUsername = entity.enumeratorUsername,
                        workflowStatus = entity.workflowStatus,
                        revisionCount = entity.revisionCount,
                        createdAt = entity.createdAt,
                        updatedAt = entity.updatedAt
                    )
                }

                val preview = importer.inspectAndValidatePackage(
                    packageFile = pkgFile,
                    existingSurveys = localSurveys
                )

                val (items, summary) = conflictEngine.analyzeConflicts(
                    incomingSurveys = preview.surveyItems,
                    incomingRevisions = preview.revisionItems,
                    existingSurveys = localSurveys
                )

                withContext(Dispatchers.Main) {
                    importPreview = preview
                    mergeItems = items
                    mergeSummary = summary
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPackages()
    }

    LaunchedEffect(selectedPackageFile) {
        selectedPackageFile?.let { analyzePackage(it) }
    }

    val filteredItems = remember(mergeItems, selectedTab) {
        when (selectedTab) {
            "CONFLICT" -> mergeItems.filter { it.conflictType == RecordConflictType.CONFLICT }
            "UPDATE" -> mergeItems.filter { it.conflictType == RecordConflictType.UPDATE_AVAILABLE }
            "NEW" -> mergeItems.filter { it.conflictType == RecordConflictType.NEW_RECORD }
            "DUPLICATE" -> mergeItems.filter { it.conflictType == RecordConflictType.DUPLICATE }
            else -> mergeItems
        }
    }

    val acceptedCount = mergeItems.count { it.supervisorDecision == SupervisorMergeDecision.ACCEPT_INCOMING }
    val keptLocalCount = mergeItems.count { it.supervisorDecision == SupervisorMergeDecision.KEEP_EXISTING }
    val reviewLaterCount = mergeItems.count { it.supervisorDecision == SupervisorMergeDecision.REVIEW_LATER || it.supervisorDecision == SupervisorMergeDecision.PENDING_REVIEW }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate950,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مراجعة ودمج الحزم الميدانية (Phase 9.2)",
                            color = Slate100,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "كشف التعارضات والدمج المنضبط تحت إشراف المشرف",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(ScreenRoute.SurveySyncImport) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Slate300)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
            )
        },
        bottomBar = {
            if (mergeItems.isNotEmpty()) {
                Surface(
                    color = Slate900,
                    shadowElevation = 8.dp,
                    modifier = Modifier.border(1.dp, Slate800, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "حالة القرارات المحددة:",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "$acceptedCount قبول | $keptLocalCount محلي | $reviewLaterCount تأجيل",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = !isExecutingMerge && (acceptedCount > 0 || keptLocalCount > 0),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isExecutingMerge) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Slate950, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري الدمج...", color = Slate950, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = Slate950, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تنفيذ الدمج المعتمد ($acceptedCount)", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Package Selector Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اختيار حزمة المزامنة للمراجعة:",
                                color = Slate200,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { loadPackages() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Sky400, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (availablePackages.isEmpty()) {
                            Text(
                                text = "لا توجد حزم .ywsync متوفرة حالياً في مسارات التخزين.",
                                color = Slate500,
                                fontSize = 11.sp
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(availablePackages) { pkg ->
                                    val isSelected = selectedPackageFile?.name == pkg.name
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Slate800 else Slate950)
                                            .clickable { selectedPackageFile = pkg }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pkg.name,
                                            color = if (isSelected) Emerald400 else Slate300,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Sky400)
                    }
                }
            } else if (importPreview != null && mergeSummary != null) {
                val preview = importPreview!!
                val summary = mergeSummary!!

                // 2. Metrics and Conflict Summary
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "معرف الحزمة: ${preview.packageId}", color = Sky400, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(text = "المصدر: ${preview.senderUsername} (${preview.district} - ${preview.governorate})", color = Slate400, fontSize = 10.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (preview.checksumVerified) Emerald950 else Rose950)
                                        .border(1.dp, if (preview.checksumVerified) Emerald500 else Rose500, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (preview.checksumVerified) "البصمة سليمة" else "خطأ في البصمة",
                                        color = if (preview.checksumVerified) Emerald400 else Rose400,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Metric Cards Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                MergeMetricPill(
                                    label = "الكل",
                                    count = summary.totalIncoming,
                                    color = Slate200,
                                    bgColor = Slate800,
                                    modifier = Modifier.weight(1f)
                                )
                                MergeMetricPill(
                                    label = "جديدة",
                                    count = summary.newRecordsCount,
                                    color = Emerald400,
                                    bgColor = Emerald950,
                                    modifier = Modifier.weight(1f)
                                )
                                MergeMetricPill(
                                    label = "تحديثات",
                                    count = summary.updatesCount,
                                    color = Sky400,
                                    bgColor = Slate800,
                                    modifier = Modifier.weight(1f)
                                )
                                MergeMetricPill(
                                    label = "تعارضات",
                                    count = summary.conflictsCount,
                                    color = Rose400,
                                    bgColor = Rose950,
                                    modifier = Modifier.weight(1f)
                                )
                                MergeMetricPill(
                                    label = "مكررة",
                                    count = summary.duplicatesCount,
                                    color = Slate400,
                                    bgColor = Slate800,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 3. Batch Action Shortcuts
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                mergeItems = mergeItems.map { item ->
                                    if (item.conflictType == RecordConflictType.NEW_RECORD || item.conflictType == RecordConflictType.UPDATE_AVAILABLE) {
                                        item.copy(supervisorDecision = SupervisorMergeDecision.ACCEPT_INCOMING)
                                    } else item
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400)
                        ) {
                            Text("قبول الجديد والتحديثات", fontSize = 9.5.sp, textAlign = TextAlign.Center)
                        }

                        OutlinedButton(
                            onClick = {
                                mergeItems = mergeItems.map { item ->
                                    if (item.conflictType == RecordConflictType.CONFLICT) {
                                        item.copy(supervisorDecision = SupervisorMergeDecision.REVIEW_LATER)
                                    } else item
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400)
                        ) {
                            Text("تأجيل كل التعارضات", fontSize = 9.5.sp, textAlign = TextAlign.Center)
                        }

                        OutlinedButton(
                            onClick = {
                                mergeItems = mergeItems.map { item ->
                                    if (item.conflictType == RecordConflictType.DUPLICATE) {
                                        item.copy(supervisorDecision = SupervisorMergeDecision.KEEP_EXISTING)
                                    } else item
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                        ) {
                            Text("تجاهل المكررات", fontSize = 9.5.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                // 4. Tab Selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MergeTabChip(title = "الكل (${mergeItems.size})", isSelected = selectedTab == "ALL", onClick = { selectedTab = "ALL" })
                        MergeTabChip(title = "تعارضات (${summary.conflictsCount})", isSelected = selectedTab == "CONFLICT", onClick = { selectedTab = "CONFLICT" }, highlight = summary.conflictsCount > 0)
                        MergeTabChip(title = "تحديثات (${summary.updatesCount})", isSelected = selectedTab == "UPDATE", onClick = { selectedTab = "UPDATE" })
                        MergeTabChip(title = "جديدة (${summary.newRecordsCount})", isSelected = selectedTab == "NEW", onClick = { selectedTab = "NEW" })
                        MergeTabChip(title = "مكررة (${summary.duplicatesCount})", isSelected = selectedTab == "DUPLICATE", onClick = { selectedTab = "DUPLICATE" })
                    }
                }

                // 5. Survey Merge Item Cards
                if (filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد استمارات في هذا التصنيف.", color = Slate500, fontSize = 11.sp)
                        }
                    }
                } else {
                    items(filteredItems, key = { it.surveyUUID + it.recordId }) { item ->
                        SurveyMergeCard(
                            item = item,
                            onDecisionChanged = { newDecision ->
                                mergeItems = mergeItems.map {
                                    if (it.surveyUUID == item.surveyUUID) {
                                        it.copy(supervisorDecision = newDecision)
                                    } else it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog before executing merge
    if (showConfirmDialog && importPreview != null) {
        val preview = importPreview!!
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = Slate900,
            title = {
                Text(
                    text = "تأكيد اعتماد الدمج المنضبط",
                    color = Slate100,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "سيتم تطبيق القرارات المعتمدة على قاعدة بيانات التطبيق المحلية المشرفة كالتالي:",
                        color = Slate300,
                        fontSize = 11.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate950),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "• قبول واستيراد استمارات واردة: $acceptedCount", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "• الاحتفاظ بالبيانات المحلية وتجاهل الوارد: $keptLocalCount", color = Slate300, fontSize = 10.sp)
                            Text(text = "• تأجيل القرار للاستمارات المعلقة: $reviewLaterCount", color = Amber400, fontSize = 10.sp)
                            Text(text = "• إنشاء لقطات مراجعة تاريخية قبل التعديل: نعم (تلقائي)", color = Sky400, fontSize = 10.sp)
                            Text(text = "• توثيق سجلات الرقابة والتدقيق (AuditLog): نعم (تلقائي)", color = Sky400, fontSize = 10.sp)
                        }
                    }

                    OutlinedTextField(
                        value = supervisorReasonInput,
                        onValueChange = { supervisorReasonInput = it },
                        label = { Text("سبب وملاحظات الاعتماد الرقابي", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Slate200),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isExecutingMerge = true
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val result = mergeExecutor.executeMerge(
                                    packageId = preview.packageId,
                                    packageExtractedDir = preview.packageFile.parentFile,
                                    reviewedItems = mergeItems,
                                    supervisorActorId = "supervisor_admin",
                                    supervisorRole = "DISTRICT_SUPERVISOR",
                                    globalReason = supervisorReasonInput
                                )
                                withContext(Dispatchers.Main) {
                                    mergeResult = result
                                    isExecutingMerge = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تأكيد وحفظ الآن", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("إلغاء", color = Slate400, fontSize = 11.sp)
                }
            }
        )
    }

    // Success & Audit Result Dialog
    mergeResult?.let { result ->
        AlertDialog(
            onDismissRequest = { mergeResult = null },
            containerColor = Slate900,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (result.isSuccess) Emerald400 else Amber400,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (result.isSuccess) "اكتمل الدمج الرقابي بنجاح" else "اكتمل الدمج مع وجود ملاحظات",
                        color = Slate100,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = result.summaryMessageAr, color = Slate300, fontSize = 11.sp)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate950),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "سجلات الرقابة المضافة (AuditLog): ${result.auditLogsCreated}", color = Sky400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "لقطات المراجعات المحفوظة (Revisions): ${result.revisionsCreated}", color = Sky400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(text = "المرفقات المحفوظة محلياً: ${result.attachmentsImported}", color = Slate300, fontSize = 10.sp)
                        }
                    }

                    if (result.errors.isNotEmpty()) {
                        Text(text = "الأخطاء:", color = Rose400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        result.errors.forEach { err ->
                            Text(text = "• $err", color = Rose300, fontSize = 9.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        mergeResult = null
                        onNavigate(ScreenRoute.SurveySyncImport)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky500),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("تم والعودة لقائمة الحزم", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun SurveyMergeCard(
    item: SurveyMergeItem,
    onDecisionChanged: (SupervisorMergeDecision) -> Unit
) {
    var isExpanded by remember { mutableStateOf(item.conflictType == RecordConflictType.CONFLICT) }

    val (badgeBg, badgeBorder, badgeText) = when (item.conflictType) {
        RecordConflictType.NEW_RECORD -> Triple(Emerald950, Emerald500, Emerald400)
        RecordConflictType.UPDATE_AVAILABLE -> Triple(Slate800, Sky500, Sky400)
        RecordConflictType.DUPLICATE -> Triple(Slate950, Slate700, Slate400)
        RecordConflictType.CONFLICT -> Triple(Rose950, Rose500, Rose400)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (item.conflictType == RecordConflictType.CONFLICT) Rose900 else Slate800, RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.recordId,
                            color = Slate100,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${item.incomingRecord.surveyType.displayNameAr})",
                            color = Slate400,
                            fontSize = 9.5.sp
                        )
                    }
                    Text(
                        text = "UUID: ${item.surveyUUID}",
                        color = Slate500,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.conflictType.titleAr,
                        color = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Revision & Location metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الموقع: ${item.incomingRecord.districtCode} - ${item.incomingRecord.uzlahCode}",
                    color = Slate400,
                    fontSize = 9.sp
                )
                Text(
                    text = if (item.existingRecord != null) {
                        "مراجعة محلية: #${item.existingRecord.revisionCount} | واردة: #${item.incomingRecord.revisionCount}"
                    } else {
                        "مراجعة أولى: #${item.incomingRecord.revisionCount}"
                    },
                    color = Slate400,
                    fontSize = 9.sp
                )
            }

            // Difference Table Toggle Button
            if (item.differences.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate950)
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مقارنة الحقول المتغيرة (${item.differences.size} حقول)",
                        color = Amber400,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.differences.forEach { diff ->
                            FieldDifferenceRow(diff = diff)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Slate800)
            Spacer(modifier = Modifier.height(8.dp))

            // Supervisor Decision Action Buttons
            Text(text = "قرار المشرف:", color = Slate400, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DecisionButton(
                    text = "قبول الوارد",
                    icon = Icons.Default.Check,
                    isSelected = item.supervisorDecision == SupervisorMergeDecision.ACCEPT_INCOMING,
                    selectedColor = Emerald500,
                    onClick = { onDecisionChanged(SupervisorMergeDecision.ACCEPT_INCOMING) },
                    modifier = Modifier.weight(1f)
                )

                DecisionButton(
                    text = "الاحتفاظ بالمحلي",
                    icon = Icons.Default.Block,
                    isSelected = item.supervisorDecision == SupervisorMergeDecision.KEEP_EXISTING,
                    selectedColor = Sky500,
                    onClick = { onDecisionChanged(SupervisorMergeDecision.KEEP_EXISTING) },
                    modifier = Modifier.weight(1f)
                )

                DecisionButton(
                    text = "تأجيل القرار",
                    icon = Icons.Default.Schedule,
                    isSelected = item.supervisorDecision == SupervisorMergeDecision.REVIEW_LATER,
                    selectedColor = Amber500,
                    onClick = { onDecisionChanged(SupervisorMergeDecision.REVIEW_LATER) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun FieldDifferenceRow(diff: FieldDifference) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate950),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = diff.fieldLabelAr,
                color = Slate300,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "المحلي الحالي:", color = Slate500, fontSize = 8.sp)
                    Text(
                        text = diff.existingValue ?: "—",
                        color = Slate300,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.CenterVertically)
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(text = "الوارد في الحزمة:", color = Sky400, fontSize = 8.sp)
                    Text(
                        text = diff.incomingValue ?: "—",
                        color = Sky300,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DecisionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    selectedColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.2f) else Slate950)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) selectedColor else Slate800,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else Slate400,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                color = if (isSelected) selectedColor else Slate400,
                fontSize = 8.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun MergeMetricPill(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    bgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$count", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = Slate400, fontSize = 8.sp)
        }
    }
}

@Composable
fun MergeTabChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Sky500 else if (highlight) Rose950 else Slate950)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Slate950 else if (highlight) Rose400 else Slate400,
            fontSize = 9.sp,
            fontWeight = if (isSelected || highlight) FontWeight.Bold else FontWeight.Normal
        )
    }
}
