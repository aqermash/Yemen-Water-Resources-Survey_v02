package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.excel.ExcelExporter
import com.yemen.watersurvey.core.pdf.PdfStampingEngine
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*

/**
 * Native Export Screen Composable for exporting offline survey datasets into genuine Multi-Sheet OOXML (.xlsx) Excel workbooks and Official Printable PDFs.
 */
@Composable
fun ExportScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExportingExcel by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }

    val sampleRecords = listOf(
        SurveyRecord(
            recordId = "rec-301",
            surveyType = SurveyType.WELL,
            admin1Pcode = "YE30",
            admin2Pcode = "YE3002",
            admin3Pcode = "YE300201",
            villageReferenceId = "YE30020101",
            enumeratorId = "usr-001",
            enumeratorUsername = "ahmed_enum",
            workflowStatus = "APPROVED",
            revisionCount = 2,
            createdAt = "2026-08-13 09:30:00",
            updatedAt = "2026-08-13 10:15:00",
            gpsPoint = GpsLocationResult(
                latitude = 16.9421,
                longitude = 43.7612,
                altitudeM = 1820.0,
                accuracyM = 4.2f,
                quality = GpsAccuracyQuality.EXCELLENT,
                capturedAt = "2026-08-13 09:31:00"
            ),
            wellDetails = WellDetails(
                wellNameAr = "بئر المقاش الارتوازي",
                wellType = "ارتوازي حفر عميق",
                wellDepthM = 240.0,
                pumpingMechanism = "مضخة غاطسة بالكهرباء",
                operationalStatus = "شغال بنشاط"
            ),
            attachments = listOf(
                AttachmentInfo(
                    attachmentId = "att-101",
                    surveyId = "rec-301",
                    attachmentType = "PHOTO",
                    filePath = "attachments/IMG_20260813_093100_a1b2c3.jpg",
                    fileName = "IMG_20260813_093100_a1b2c3.jpg",
                    fileSize = 285000L,
                    timestamp = "2026-08-13 09:31:05"
                )
            )
        ),
        SurveyRecord(
            recordId = "rec-302",
            surveyType = SurveyType.SPRING,
            admin1Pcode = "YE30",
            admin2Pcode = "YE3001",
            admin3Pcode = "YE300101",
            villageReferenceId = "YE30010101",
            enumeratorId = "usr-001",
            enumeratorUsername = "ahmed_enum",
            workflowStatus = "COMPLETED",
            revisionCount = 1,
            createdAt = "2026-08-13 11:00:00",
            updatedAt = "2026-08-13 11:00:00",
            gpsPoint = GpsLocationResult(
                latitude = 16.8123,
                longitude = 43.2411,
                altitudeM = 2100.0,
                accuracyM = 8.5f,
                quality = GpsAccuracyQuality.GOOD,
                capturedAt = "2026-08-13 11:01:00"
            ),
            springDetails = SpringDetails(
                springNameAr = "عين النظير الجارية",
                flowRateLps = 12.5,
                waterClarity = "عذبة ونقية جداً",
                dischargeSeasonality = "دائم التدفق طوال العام"
            )
        ),
        SurveyRecord(
            recordId = "rec-303",
            surveyType = SurveyType.DAM,
            admin1Pcode = "YE13",
            admin2Pcode = "YE1305",
            admin3Pcode = "YE130501",
            villageReferenceId = "YE13050101",
            enumeratorId = "usr-002",
            enumeratorUsername = "super_district",
            workflowStatus = "UNDER_REVIEW",
            revisionCount = 3,
            createdAt = "2026-08-13 12:00:00",
            updatedAt = "2026-08-13 12:30:00",
            gpsPoint = GpsLocationResult(
                latitude = 15.3421,
                longitude = 44.1822,
                altitudeM = 2300.0,
                accuracyM = 12.0f,
                quality = GpsAccuracyQuality.ACCEPTABLE_WITH_WARNING,
                capturedAt = "2026-08-13 12:01:00"
            ),
            damDetails = DamDetails(
                damNameAr = "سد متنة التحويلي",
                structureType = "سد تحويلي خرساني",
                storageCapacityM3 = 450000.0,
                damHeightM = 18.5,
                structuralCondition = "جيدة جداً"
            )
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "تصدير البيانات الميدانية والتقارير الرسمية",
            color = Slate100,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "توليد ملفات Excel حقيقية (.xlsx) بأربع صفحات واستمارات PDF رسمية بدون إنترنت",
            color = Slate400,
            fontSize = 11.sp
        )

        Divider(color = Slate800)

        // Option 1: Multi-Sheet Genuine OOXML Excel Workbook
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Emerald950)
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = "Excel", tint = Emerald400)
                    }

                    Column {
                        Text(text = "تصدير جدول Excel حقيقي (.xlsx)", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "4 صفحات: الآبار، العيون، السدود، وسجل العمليات الشامل", color = Slate400, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        isExportingExcel = true
                        val exporter = ExcelExporter(context)
                        val res = exporter.exportSurveysToExcel(sampleRecords, "ahmed_enum")
                        exportSuccessMessage = "تم تصدير ملف OOXML .xlsx بنجاح (4 صفحات): ${res.fileName}"
                        isExportingExcel = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExportingExcel) {
                        CircularProgressIndicator(color = Slate950, modifier = Modifier.size(18.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = Slate950)
                            Text("توليد ملف .xlsx حقيقي الآن", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Option 2: Official Form Stamped PDF Generator (Phase 7)
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Sky950)
                            .border(1.dp, Sky500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Sky400)
                    }

                    Column {
                        Text(text = "توليد استمارات PDF رسمية مختومة (A4)", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "ختم البيانات بدقة الإحداثيات (X,Y) على نماذج الآبار والعيون والسدود", color = Slate400, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        isExportingPdf = true
                        val engine = PdfStampingEngine(context)
                        // Stamp first record
                        val sampleRecord = sampleRecords.first()
                        val res = engine.stampSurveyToPdf(sampleRecord)
                        exportSuccessMessage = "تم توليد وختم الاستمارة الرسمية PDF بنجاح: ${res.fileName}"
                        isExportingPdf = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Sky500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isExportingPdf) {
                        CircularProgressIndicator(color = Slate950, modifier = Modifier.size(18.dp))
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Slate950)
                            Text("توليد استمارة PDF رسمية (Well)", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Option 3: Survey Data Exchange Package Generator (Phase 9.0)
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Cyan950)
                            .border(1.dp, Cyan500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SyncAlt, contentDescription = "Sync", tint = Cyan400)
                    }

                    Column {
                        Text(text = "حزم التبادل الميداني الموقعة (.ywsync)", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "تصدير حزمة تبادل مشفرة ببيان manifest وتوقيع SHA-256 للنقل بـ USB / SD / Bluetooth", color = Slate400, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        onNavigate(ScreenRoute.SurveySyncExport)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = Slate950)
                        Text("فتح منصة التصدير والفلترة الميدانية (.ywsync)", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Option 4: Supervisor Survey Package Import & Validation Engine (Phase 9.1)
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Amber950)
                            .border(1.dp, Amber500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = "Import", tint = Amber400)
                    }

                    Column {
                        Text(text = "استيراد وفحص حزم المشرفين الميدانية (.ywsync)", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "فحص البصمة المشفرة SHA-256 وكشف الاستمارات المكررة بدون مساس بقاعدة البيانات", color = Slate400, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        onNavigate(ScreenRoute.SurveySyncImport)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, tint = Slate950)
                        Text("فتح منصة فحص واستيراد حزم المزامنة (.ywsync)", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Option 5: Offline Supervisor Synchronization Workspace (Phase 9.3)
        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Slate800, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Emerald950)
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Dashboard, contentDescription = "Workspace", tint = Emerald400)
                    }

                    Column {
                        Text(text = "مساحة عمل مزامنة المشرف (صندوق الوارد وإدارة الحزم)", color = Slate100, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "إدارة حزم الباحثين المتعددة، تتبع دورة الحياة، مراقبة التعارضات، وإحصائيات المديرية", color = Slate400, fontSize = 10.sp)
                    }
                }

                Button(
                    onClick = {
                        onNavigate(ScreenRoute.SupervisorSyncDashboard)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Dashboard, contentDescription = null, tint = Slate950)
                        Text("فتح مساحة عمل مزامنة المشرفين (Workspace)", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Export Status Alert Message
        exportSuccessMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Emerald950),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Emerald500, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = Emerald400)
                    Text(text = msg, color = Emerald300, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
