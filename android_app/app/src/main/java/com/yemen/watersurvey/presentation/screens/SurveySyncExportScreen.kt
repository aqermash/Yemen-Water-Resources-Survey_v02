package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.sync.SurveySyncExporter
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*
import java.io.File

/**
 * Screen for configuring and generating .ywsync offline Survey Data Exchange Packages.
 * Facilitates hierarchical transfer (Enumerator -> District -> Governorate -> National)
 * over USB OTG, SD Card, or Bluetooth with zero cloud/network dependency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveySyncExportScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exporter = remember { SurveySyncExporter(context) }
    val scrollState = rememberScrollState()

    // Sample active records in repository for simulation & preview
    val sampleSurveys = remember {
        listOf(
            SurveyRecord(
                recordId = "WELL-YE-SA-D02-E01-000142",
                surveyType = SurveyType.WELL,
                admin1Pcode = "YE30",
                admin2Pcode = "YE3002",
                admin3Pcode = "YE300201",
                villageReferenceId = "YE30020104",
                enumeratorId = "usr-enum-301",
                enumeratorUsername = "ahmed_enum",
                workflowStatus = "APPROVED",
                revisionCount = 2,
                createdAt = "2026-08-14 09:15:00",
                updatedAt = "2026-08-14 11:30:00",
                gpsPoint = GpsLocationResult(
                    latitude = 16.9412,
                    longitude = 43.7654,
                    altitudeM = 1845.0,
                    accuracyM = 3.8f,
                    quality = GpsAccuracyQuality.EXCELLENT,
                    capturedAt = "2026-08-14 09:16:20"
                ),
                wellDetails = WellDetails(
                    wellNameAr = "بئر الغيل الارتوازي المركزي",
                    wellType = "ارتوازي حفر آلي",
                    wellDepthM = 210.0,
                    pumpingMechanism = "مضخة غاطسة - طاقة شمسية",
                    operationalStatus = "شغال بكفاءة عالية"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-well-01",
                        surveyId = "WELL-YE-SA-D02-E01-000142",
                        attachmentType = "PHOTO_WELLHEAD",
                        filePath = "photos/WELL_000142_head.jpg",
                        fileName = "WELL_000142_head.jpg",
                        fileSize = 420000L,
                        timestamp = "2026-08-14 09:16:30"
                    ),
                    AttachmentInfo(
                        attachmentId = "att-well-02",
                        surveyId = "WELL-YE-SA-D02-E01-000142",
                        attachmentType = "PHOTO_SOLAR",
                        filePath = "photos/WELL_000142_solar.jpg",
                        fileName = "WELL_000142_solar.jpg",
                        fileSize = 385000L,
                        timestamp = "2026-08-14 09:17:00"
                    )
                )
            ),
            SurveyRecord(
                recordId = "SPRING-YE-SA-D01-E01-000089",
                surveyType = SurveyType.SPRING,
                admin1Pcode = "YE30",
                admin2Pcode = "YE3001",
                admin3Pcode = "YE300101",
                villageReferenceId = "YE30010102",
                enumeratorId = "usr-enum-301",
                enumeratorUsername = "ahmed_enum",
                workflowStatus = "COMPLETED",
                revisionCount = 1,
                createdAt = "2026-08-14 10:20:00",
                updatedAt = "2026-08-14 10:20:00",
                gpsPoint = GpsLocationResult(
                    latitude = 16.8234,
                    longitude = 43.2519,
                    altitudeM = 2120.0,
                    accuracyM = 5.2f,
                    quality = GpsAccuracyQuality.GOOD,
                    capturedAt = "2026-08-14 10:21:00"
                ),
                springDetails = SpringDetails(
                    springNameAr = "عين وادي علاف الطبيعية",
                    flowRateLps = 18.2,
                    waterClarity = "عذبة ونقية جداً",
                    dischargeSeasonality = "دائم التدفق"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-spring-01",
                        surveyId = "SPRING-YE-SA-D01-E01-000089",
                        attachmentType = "PHOTO_OUTLET",
                        filePath = "photos/SPRING_000089_outlet.jpg",
                        fileName = "SPRING_000089_outlet.jpg",
                        fileSize = 310000L,
                        timestamp = "2026-08-14 10:21:30"
                    )
                )
            ),
            SurveyRecord(
                recordId = "DAM-YE-SA-D03-E02-000034",
                surveyType = SurveyType.DAM,
                admin1Pcode = "YE30",
                admin2Pcode = "YE3003",
                admin3Pcode = "YE300302",
                villageReferenceId = "YE30030201",
                enumeratorId = "usr-sup-302",
                enumeratorUsername = "supervisor_district",
                workflowStatus = "UNDER_REVIEW",
                revisionCount = 3,
                createdAt = "2026-08-14 11:45:00",
                updatedAt = "2026-08-14 12:15:00",
                gpsPoint = GpsLocationResult(
                    latitude = 16.7119,
                    longitude = 43.6891,
                    altitudeM = 1950.0,
                    accuracyM = 9.4f,
                    quality = GpsAccuracyQuality.GOOD,
                    capturedAt = "2026-08-14 11:46:10"
                ),
                damDetails = DamDetails(
                    damNameAr = "سد الركوة التخزيني",
                    structureType = "سد ركامي مع مفيض خرساني",
                    storageCapacityM3 = 680000.0,
                    damHeightM = 22.0,
                    structuralCondition = "جيدة مع تسرب طفيف في المفيض"
                ),
                attachments = listOf(
                    AttachmentInfo(
                        attachmentId = "att-dam-01",
                        surveyId = "DAM-YE-SA-D03-E02-000034",
                        attachmentType = "PHOTO_SPILLWAY",
                        filePath = "photos/DAM_000034_spillway.jpg",
                        fileName = "DAM_000034_spillway.jpg",
                        fileSize = 512000L,
                        timestamp = "2026-08-14 11:47:00"
                    )
                )
            )
        )
    }

    val sampleRevisions = remember {
        listOf(
            SurveyRevisionRecord(
                revisionId = "rev-001",
                recordId = "WELL-YE-SA-D02-E01-000142",
                revisionNumber = 1,
                modifiedBy = "ahmed_enum",
                modifiedAt = "2026-08-14 09:15:00",
                reasonForChange = "إنشاء الاستمارة الأولية للمسح",
                previousStatus = "DRAFT",
                newStatus = "COMPLETED",
                changedFields = listOf("all")
            ),
            SurveyRevisionRecord(
                revisionId = "rev-002",
                recordId = "WELL-YE-SA-D02-E01-000142",
                revisionNumber = 2,
                modifiedBy = "supervisor_district",
                modifiedAt = "2026-08-14 11:30:00",
                reasonForChange = "اعتماد الاستمارة ومطابقة إحداثيات GPS",
                previousStatus = "COMPLETED",
                newStatus = "APPROVED",
                changedFields = listOf("workflowStatus")
            ),
            SurveyRevisionRecord(
                revisionId = "rev-003",
                recordId = "DAM-YE-SA-D03-E02-000034",
                revisionNumber = 2,
                modifiedBy = "supervisor_district",
                modifiedAt = "2026-08-14 12:15:00",
                reasonForChange = "إعادة التقييم الميداني لحالة المفيض",
                previousStatus = "COMPLETED",
                newStatus = "UNDER_REVIEW",
                changedFields = listOf("damDetails.structuralCondition", "workflowStatus")
            )
        )
    }

    // Filter states
    var selectedScopeType by remember { mutableStateOf("ALL") } // ALL, WELL, SPRING, DAM
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, APPROVED, COMPLETED, UNDER_REVIEW, DRAFT
    var selectedSenderRole by remember { mutableStateOf("ENUMERATOR") } // ENUMERATOR, DISTRICT_SUPERVISOR, GOVERNORATE_SUPERVISOR
    var deviceIdInput by remember { mutableStateOf("DEV-YEM-8F92A104") }
    var usernameInput by remember { mutableStateOf("ahmed_enum") }
    var governorateInput by remember { mutableStateOf("صعدة") }
    var districtInput by remember { mutableStateOf("سحار") }

    // State for export process
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<SyncExportResult?>(null) }

    // Calculate current filter
    val currentFilter = remember(selectedScopeType, selectedStatusFilter) {
        val surveyType = when (selectedScopeType) {
            "WELL" -> SurveyType.WELL
            "SPRING" -> SurveyType.SPRING
            "DAM" -> SurveyType.DAM
            else -> null
        }
        val status = if (selectedStatusFilter == "ALL") null else selectedStatusFilter
        SyncExportFilter(surveyType = surveyType, workflowStatus = status)
    }

    // Live Stats
    val stats = remember(currentFilter, sampleSurveys) {
        exporter.calculateExportStats(sampleSurveys, currentFilter)
    }
    val matchingSurveyCount = stats.first
    val (matchingAttachmentCount, matchingAttachmentSize) = stats.second

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "تصدير حزمة التبادل الميداني (.ywsync)",
                    color = Slate100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "توليد حزمة تبادل مشفرة وموقعة بدون إنترنت للنقل عبر SD Card أو USB OTG أو البلوتوث",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = { onNavigate(ScreenRoute.Dashboard) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Slate400)
            }
        }

        Divider(color = Slate800)

        // 1. Live Scope Summary Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "ملخص محتويات الحزمة الحالية",
                    color = Cyan400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$matchingSurveyCount", color = Slate100, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "استمارة مطابقة", color = Slate400, fontSize = 10.sp)
                    }
                    Divider(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp),
                        color = Slate800
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$matchingAttachmentCount", color = Slate100, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "مرفق صور", color = Slate400, fontSize = 10.sp)
                    }
                    Divider(
                        modifier = Modifier
                            .height(32.dp)
                            .width(1.dp),
                        color = Slate800
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val sizeKb = matchingAttachmentSize / 1024
                        Text(text = "$sizeKb KB", color = Slate100, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(text = "حجم الوسائط", color = Slate400, fontSize = 10.sp)
                    }
                }
            }
        }

        // 2. Scope & Filter Options
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "1. تحديد نطاق التصدير والفلاتر",
                    color = Slate200,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Survey Type Filter
                Text(text = "نوع المنشأة المائية:", color = Slate400, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val types = listOf(
                        "ALL" to "الكل",
                        "WELL" to "الآبار",
                        "SPRING" to "العيون",
                        "DAM" to "السدود"
                    )
                    for ((key, label) in types) {
                        val isSelected = selectedScopeType == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Cyan500.copy(alpha = 0.2f) else Slate950)
                                .border(1.dp, if (isSelected) Cyan500 else Slate800, RoundedCornerShape(8.dp))
                                .clickable { selectedScopeType = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Cyan300 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Status Filter
                Text(text = "حالة سير العمل المعتمدة:", color = Slate400, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statuses = listOf(
                        "ALL" to "الكل",
                        "APPROVED" to "معتمدة",
                        "COMPLETED" to "مكتملة",
                        "UNDER_REVIEW" to "قيد المراجعة"
                    )
                    for ((key, label) in statuses) {
                        val isSelected = selectedStatusFilter == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Emerald500.copy(alpha = 0.2f) else Slate950)
                                .border(1.dp, if (isSelected) Emerald500 else Slate800, RoundedCornerShape(8.dp))
                                .clickable { selectedStatusFilter = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Emerald300 else Slate400,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 3. Sender & Device Metadata Configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "2. بيانات الجهاز والمرسل لبيان الحزمة (Manifest)",
                    color = Slate200,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // Role Selection
                Text(text = "الدور الإداري للمرسل:", color = Slate400, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val roles = listOf(
                        "ENUMERATOR" to "راصد ميداني",
                        "DISTRICT_SUPERVISOR" to "مشرف مديرية",
                        "GOVERNORATE_SUPERVISOR" to "مشرف محافظة"
                    )
                    for ((roleKey, roleLabel) in roles) {
                        val isSelected = selectedSenderRole == roleKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Sky500.copy(alpha = 0.2f) else Slate950)
                                .border(1.dp, if (isSelected) Sky500 else Slate800, RoundedCornerShape(8.dp))
                                .clickable { selectedSenderRole = roleKey }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = roleLabel,
                                color = if (isSelected) Sky300 else Slate400,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = governorateInput,
                        onValueChange = { governorateInput = it },
                        label = { Text("المحافظة", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Sky500,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate200
                        )
                    )
                    OutlinedTextField(
                        value = districtInput,
                        onValueChange = { districtInput = it },
                        label = { Text("المديرية", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Sky500,
                            unfocusedBorderColor = Slate800,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate200
                        )
                    )
                }

                OutlinedTextField(
                    value = deviceIdInput,
                    onValueChange = { deviceIdInput = it },
                    label = { Text("معرف الجهاز الميداني (Device ID)", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Sky500,
                        unfocusedBorderColor = Slate800,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate200
                    )
                )
            }
        }

        // 4. Package Internal Structure Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "الهيكل الداخلي لحزمة التبادل (.ywsync):",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "📦 SYNC_[Role]_[Date].ywsync\n" +
                            " ├── 📄 manifest.json (بيان الحزمة والتوقيع الرقمي)\n" +
                            " ├── 📄 metadata.json (نطاق التصدير وتفاصيل الجهاز)\n" +
                            " ├── 📄 surveys.json (بيانات الاستمارات الميدانية المحددة)\n" +
                            " ├── 📄 revisions.json (سجلات التعديلات والمراجعات)\n" +
                            " ├── 📁 attachments/ (الصور المرفوعة فقط معزولة حسب Survey ID)\n" +
                            " └── 🔑 checksum.sha256 (رمز البصمة التراكمي للتحقق من سلامة النقل)",
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }

        // 5. Generate Package Action Button
        Button(
            onClick = {
                isExporting = true
                // Create mock sample files for attachments if needed
                for (survey in sampleSurveys) {
                    for (att in survey.attachments) {
                        val attFile = File(context.filesDir, att.filePath)
                        if (!attFile.exists()) {
                            attFile.parentFile?.mkdirs()
                            attFile.writeText("MOCK_PHOTO_DATA_FOR_${att.fileName}_${att.attachmentId}")
                        }
                    }
                }

                val result = exporter.exportSyncPackage(
                    allSurveys = sampleSurveys,
                    allRevisions = sampleRevisions,
                    filter = currentFilter,
                    sourceDeviceId = deviceIdInput,
                    senderRole = selectedSenderRole,
                    senderUsername = usernameInput,
                    district = districtInput,
                    governorate = governorateInput
                )
                exportResult = result
                isExporting = false
            },
            enabled = !isExporting && matchingSurveyCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isExporting) {
                CircularProgressIndicator(color = Slate950, modifier = Modifier.size(20.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = Slate950)
                    Text(
                        text = "توليد حزمة التبادل الميداني (.ywsync)",
                        color = Slate950,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 6. Export Result Card
        exportResult?.let { res ->
            when (res) {
                is SyncExportResult.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Emerald950.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Emerald500, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Emerald400)
                                Text(
                                    text = "تم إنشاء وتوقيع حزمة التبادل بنجاح",
                                    color = Emerald300,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "اسم الملف: ${res.packageFile.name}",
                                color = Slate200,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "المسار: ${res.packageFile.absolutePath}",
                                color = Slate400,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "عدد الاستمارات: ${res.surveyCount} | المرفقات: ${res.attachmentCount} | الحجم: ${res.packageSizeBytes / 1024} KB",
                                color = Slate300,
                                fontSize = 11.sp
                            )

                            // SHA-256 Checksum badge
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Slate950),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "بصمة التحقق الرقمية SHA-256:",
                                        color = Cyan400,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = res.checksumSha256,
                                        color = Slate300,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Text(
                                text = "جاهز للنقل الميداني الآن عبر كرت الذاكرة SD أو وصلة USB OTG أو مشاركة البلوتوث.",
                                color = Emerald400,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                is SyncExportResult.Failure -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Rose950.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Rose500, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = "Error", tint = Rose400)
                                Text(
                                    text = "فشل توليد الحزمة",
                                    color = Rose300,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(text = res.errorMessage, color = Rose200, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
