package com.yemen.watersurvey.presentation.screens

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
import com.yemen.watersurvey.core.sync.SurveySyncExporter
import com.yemen.watersurvey.core.sync.SurveySyncImporter
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Supervisor Survey Package Import & Validation Screen (Phase 9.1).
 *
 * Provides a dedicated offline interface for supervisors (District/Governorate/Central) to:
 * 1. Select .ywsync packages from external/local storage.
 * 2. Unpack into an isolated sandbox staging directory without modifying the Room database.
 * 3. Inspect SHA-256 cryptographic signatures, internal ZIP structure, manifest, and metadata.
 * 4. Display a detailed preview with duplicate detection stats (Survey UUID & Package ID).
 * 5. Enable explicit supervisor Acceptance (staging for Phase 9.2 merge) or Rejection.
 * 6. Guarantee zero data insertion or database mutations before user approval.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveySyncImportScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val importer = remember { SurveySyncImporter(context) }
    val exporter = remember { SurveySyncExporter(context) }

    var availablePackages by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedPackageFile by remember { mutableStateOf<File?>(null) }
    var importPreview by remember { mutableStateOf<SyncImportPreview?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var inspectionStatus by remember { mutableStateOf(SyncPackageInspectionStatus.PENDING_VALIDATION) }
    var acceptedPackagesList by remember { mutableStateOf<List<String>>(emptyList()) }

    // Mock existing local database records to demonstrate duplicate detection
    val supervisorLocalSurveys = remember {
        listOf(
            SurveyRecord(
                recordId = "WELL-YE-30-001",
                surveyType = SurveyType.WELL,
                admin1Pcode = "YE30",
                admin2Pcode = "YE3001",
                admin3Pcode = "YE300101",
                villageReferenceId = "المقاش",
                governorateNameSnapshotAr = "صعدة",
                districtNameSnapshotAr = "سحار",
                subDistrictNameSnapshotAr = "الطلح",
                workflowStatus = "APPROVED",
                createdAt = "2026-08-10 09:30:00"
            )
        )
    }

    // Refresh list of available .ywsync packages from cache and sync_exports directories
    fun refreshAvailablePackages() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val foundFiles = mutableListOf<File>()
                val exportDir = File(context.filesDir, "sync_exports")
                if (exportDir.exists()) {
                    foundFiles.addAll(exportDir.listFiles { f -> f.extension == "ywsync" || f.extension == "ysync" } ?: emptyArray())
                }
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    foundFiles.addAll(cacheDir.listFiles { f -> f.extension == "ywsync" || f.extension == "ysync" } ?: emptyArray())
                }
                val stagingDir = importer.acceptedStagedDir
                if (stagingDir.exists()) {
                    acceptedPackagesList = stagingDir.listFiles()?.map { it.name } ?: emptyList()
                }
                withContext(Dispatchers.Main) {
                    availablePackages = foundFiles.distinctBy { it.name }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAvailablePackages()
    }

    fun inspectPackage(file: File) {
        selectedPackageFile = file
        isLoading = true
        actionStatusMessage = null
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val preview = importer.inspectAndValidatePackage(
                    packageFile = file,
                    existingSurveys = supervisorLocalSurveys,
                    knownPackageIds = acceptedPackagesList.toSet()
                )
                withContext(Dispatchers.Main) {
                    importPreview = preview
                    isLoading = false
                    inspectionStatus = when {
                        !preview.isValidStructure -> SyncPackageInspectionStatus.STRUCTURE_INVALID
                        !preview.checksumVerified -> SyncPackageInspectionStatus.CHECKSUM_MISMATCH
                        preview.isDuplicatePackage -> SyncPackageInspectionStatus.DUPLICATE_PACKAGE_WARNING
                        else -> SyncPackageInspectionStatus.VALID_READY_FOR_REVIEW
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "استيراد وفحص حزم التبادل الميداني (.ywsync)",
                    color = Slate100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "محرك المشرف لفحص السلامة المشفرة وهيكل البيانات بدون مساس بقاعدة البيانات المحلية",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = { refreshAvailablePackages() },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate900)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Sky400, modifier = Modifier.size(18.dp))
            }
        }

        Divider(color = Slate800)

        // Status Alert
        actionStatusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isErrorStatus) Rose950 else Emerald950),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isErrorStatus) Rose500 else Emerald500, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isErrorStatus) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isErrorStatus) Rose400 else Emerald400
                    )
                    Text(
                        text = msg,
                        color = if (isErrorStatus) Rose300 else Emerald300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Two-Column or Stacked View: Package Selector & Inspection Preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Panel: Available Packages on Device / Storage
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .border(1.dp, Slate800, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "الحزم المتاحة للتحقق (${availablePackages.size})",
                        color = Slate200,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Helper generate sample package button for quick offline testing
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    val sampleSurveys = listOf(
                                        SurveyRecord(
                                            recordId = "WELL-YE-30-001", // Duplicate ID to test duplicate detection
                                            surveyType = SurveyType.WELL,
                                            admin1Pcode = "YE30",
                                            admin2Pcode = "YE3001",
                                            admin3Pcode = "YE300101",
                                            villageReferenceId = "المقاش",
                                            governorateNameSnapshotAr = "صعدة",
                                            districtNameSnapshotAr = "سحار",
                                            subDistrictNameSnapshotAr = "الطلح",
                                            workflowStatus = "APPROVED",
                                            createdAt = "2026-08-14 08:00:00",
                                            wellDetails = WellDetails("بئر المقاش المحفور", "ARTESIAN", 140.0, "SOLAR", "FUNCTIONAL")
                                        ),
                                        SurveyRecord(
                                            recordId = "SPRING-YE-30-088", // New record
                                            surveyType = SurveyType.SPRING,
                                            admin1Pcode = "YE30",
                                            admin2Pcode = "YE3001",
                                            admin3Pcode = "YE300101",
                                            villageReferenceId = "الغيل",
                                            governorateNameSnapshotAr = "صعدة",
                                            districtNameSnapshotAr = "سحار",
                                            subDistrictNameSnapshotAr = "الطلح",
                                            workflowStatus = "COMPLETED",
                                            createdAt = "2026-08-14 09:30:00",
                                            springDetails = SpringDetails("عين الغيل الطبيعية", 8.5, "CLEAR", "PERENNIAL")
                                        )
                                    )
                                    val sampleRevisions = listOf(
                                        SurveyRevisionRecord(
                                            revisionId = "rev-import-test-1",
                                            recordId = "WELL-YE-30-001",
                                            revisionNumber = 1,
                                            modifiedBy = "ahmed_enum",
                                            modifiedAt = "2026-08-14 08:00:00",
                                            reasonForChange = "Initial field record entry",
                                            previousStatus = "DRAFT",
                                            newStatus = "COMPLETED"
                                        )
                                    )
                                    exporter.exportSyncPackage(
                                        allSurveys = sampleSurveys,
                                        allRevisions = sampleRevisions,
                                        filter = SyncExportFilter(),
                                        sourceDeviceId = "ENUM-TAB-SAADA-04",
                                        senderRole = "ENUMERATOR",
                                        senderUsername = "ahmed_enum",
                                        district = "سحار",
                                        governorate = "صعدة"
                                    )
                                }
                                refreshAvailablePackages()
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddBox, contentDescription = null, tint = Sky400, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("توليد حزمة تجريبية للفحص", color = Sky300, fontSize = 10.5.sp)
                    }

                    Divider(color = Slate800)

                    if (availablePackages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد ملفات .ywsync في مجلدات التخزين المحلي.\nقم بنقل الحزمة عبر USB/SD أو توليد حزمة تجريبية أعلاه.",
                                color = Slate500,
                                fontSize = 10.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availablePackages) { pkgFile ->
                                val isSelected = selectedPackageFile?.absolutePath == pkgFile.absolutePath
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Slate800 else Slate950
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            1.dp,
                                            if (isSelected) Sky500 else Slate800,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { inspectPackage(pkgFile) }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = pkgFile.name,
                                                color = Slate200,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Icon(
                                                Icons.Default.Archive,
                                                contentDescription = null,
                                                tint = if (isSelected) Sky400 else Slate600,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = "${(pkgFile.length() / 1024).coerceAtLeast(1)} KB",
                                            color = Slate400,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right Panel: Inspection Preview, Security Checksum, Duplicate Analysis
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .border(1.dp, Slate800, RoundedCornerShape(14.dp))
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = Sky500)
                            Text("جاري فك ضغط الحزمة وفحص البصمة والتكرارات...", color = Slate400, fontSize = 11.sp)
                        }
                    }
                } else if (importPreview == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, tint = Slate600, modifier = Modifier.size(44.dp))
                            Text("حدد حزمة مزامنة من القائمة لبدء الفحص والتحقق الأمني", color = Slate400, fontSize = 11.5.sp)
                        }
                    }
                } else {
                    val preview = importPreview!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title & Status Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "تقرير الفحص والمعاينة للحزمة",
                                    color = Slate100,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = preview.packageFile.name,
                                    color = Slate400,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            // Security Verification Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (preview.checksumVerified && preview.isValidStructure) Emerald950 else Rose950)
                                    .border(
                                        1.dp,
                                        if (preview.checksumVerified && preview.isValidStructure) Emerald500 else Rose500,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (preview.checksumVerified && preview.isValidStructure) "بصمة معتمدة وسليمة" else "تحذير أمني / غير صالحة",
                                    color = if (preview.checksumVerified && preview.isValidStructure) Emerald300 else Rose300,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Divider(color = Slate800)

                        // Key Metadata Overview Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PreviewStatPill(
                                label = "الجهاز المرسل",
                                value = preview.sourceDeviceId,
                                modifier = Modifier.weight(1f)
                            )
                            PreviewStatPill(
                                label = "الدور الميداني",
                                value = "${preview.senderRole} (${preview.senderUsername})",
                                modifier = Modifier.weight(1f)
                            )
                            PreviewStatPill(
                                label = "المديرية / المحافظة",
                                value = "${preview.district} - ${preview.governorate}",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Duplicate & Content Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "إجمالي الاستمارات", color = Slate400, fontSize = 9.sp)
                                    Text(text = "${preview.surveyCount}", color = Slate100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, if (preview.duplicateSurveysCount > 0) Amber500.copy(alpha = 0.5f) else Slate800, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "الاستمارات المكررة", color = Slate400, fontSize = 9.sp)
                                    Text(
                                        text = "${preview.duplicateSurveysCount}",
                                        color = if (preview.duplicateSurveysCount > 0) Amber400 else Slate400,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "استمارات جديدة", color = Slate400, fontSize = 9.sp)
                                    Text(text = "${preview.newSurveysCount}", color = Emerald400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "المرفقات والصور", color = Slate400, fontSize = 9.sp)
                                    Text(text = "${preview.attachmentCount}", color = Sky400, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // SHA-256 Checksum Display Box
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(text = "بصمة التحقق المشفرة (SHA-256 Checksum):", color = Slate400, fontSize = 9.5.sp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Slate950)
                                    .border(1.dp, Slate800, RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "المحسوبة: ${preview.calculatedChecksum.ifBlank { "غير متوفرة" }}",
                                        color = if (preview.checksumVerified) Emerald400 else Rose400,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "المعلنة:  ${preview.manifestChecksum.ifBlank { "غير متوفرة" }}",
                                        color = Slate400,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        // Errors and Warnings list
                        if (preview.validationErrors.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Rose950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Rose500, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "أخطاء الفحص الهيكلي والأمني:", color = Rose300, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    preview.validationErrors.forEach { err ->
                                        Text(text = "• $err", color = Rose200, fontSize = 9.sp)
                                    }
                                }
                            }
                        }

                        if (preview.validationWarnings.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Amber950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Amber500, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(text = "تنبيهات الفحص والتكرار:", color = Amber300, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    preview.validationWarnings.forEach { warn ->
                                        Text(text = "• $warn", color = Amber200, fontSize = 9.sp)
                                    }
                                }
                            }
                        }

                        // Detailed list of Surveys in the package
                        Text(
                            text = "الاستمارات المرفقة في الحزمة (${preview.surveyItems.size}):",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(preview.surveyItems) { item ->
                                val isDup = preview.duplicateSurveyUuids.contains(item.recordId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Slate950)
                                        .border(1.dp, if (isDup) Amber500.copy(alpha = 0.5f) else Slate800, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = item.recordId, color = Slate200, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${item.surveyType.displayNameAr} | ${item.workflowStatus}",
                                            color = Slate400,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isDup) Amber950 else Emerald950)
                                            .border(1.dp, if (isDup) Amber500 else Emerald500, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isDup) "مسجل مسبقاً (مكرر)" else "جديد كلياً",
                                            color = if (isDup) Amber300 else Emerald300,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Divider(color = Slate800)

                        // Action Buttons: Accept (Staging for Phase 9.2) or Reject
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    importer.rejectPackage(preview.packageFile)
                                    actionStatusMessage = "تم رفض الحزمة ${preview.packageId} دون أي تعديل على قاعدة البيانات."
                                    isErrorStatus = true
                                    inspectionStatus = SyncPackageInspectionStatus.REJECTED_BY_SUPERVISOR
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose400),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("رفض الحزمة", fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            importer.stageAcceptedPackage(preview.packageFile, preview.packageId)
                                        }
                                        actionStatusMessage = "تم اعتماد وفحص الحزمة وتجهيزها بأمان لمرحلة الدمج (Phase 9.2)."
                                        isErrorStatus = false
                                        inspectionStatus = SyncPackageInspectionStatus.ACCEPTED_STAGED
                                        refreshAvailablePackages()
                                    }
                                },
                                enabled = preview.isValidStructure && preview.checksumVerified,
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("اعتماد الحزمة", color = Slate950, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onNavigate(ScreenRoute.SurveyMergeReview)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Sky500),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.MergeType, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مراجعة ودمج", color = Slate950, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate950),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, Slate800, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(text = label, color = Slate500, fontSize = 8.5.sp)
            Text(
                text = value.ifBlank { "-" },
                color = Slate200,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
