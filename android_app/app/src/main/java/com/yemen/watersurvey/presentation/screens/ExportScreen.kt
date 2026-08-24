package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SyncAlt
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

import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native Export Screen Composable for exporting offline survey datasets into genuine Multi-Sheet OOXML (.xlsx) Excel workbooks and Official Printable PDFs.
 */
@Composable
fun ExportScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val surveyDao = remember { database.surveyRecordDao() }

    var isExportingExcel by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }

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

        HorizontalDivider(color = Slate800)

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
                        coroutineScope.launch {
                            isExportingExcel = true
                            try {
                                val realEntities = withContext(Dispatchers.IO) {
                                    surveyDao.getAllSurveysSync()
                                }
                                val domainRecords = realEntities.map { it.toDomainModel() }
                                val exporter = ExcelExporter(context)
                                val res = withContext(Dispatchers.IO) {
                                    exporter.exportSurveysToExcel(domainRecords, "field_enumerator")
                                }
                                exportSuccessMessage = "تم تصدير ملف OOXML .xlsx بنجاح (${res.recordsCount} سجلات - 4 صفحات): ${res.fileName}"
                            } catch (e: Exception) {
                                exportSuccessMessage = "خطأ أثناء التصدير: ${e.message}"
                            } finally {
                                isExportingExcel = false
                            }
                        }
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
                        coroutineScope.launch {
                            isExportingPdf = true
                            try {
                                val realEntities = withContext(Dispatchers.IO) {
                                    surveyDao.getAllSurveysSync()
                                }
                                if (realEntities.isEmpty()) {
                                    exportSuccessMessage = "لا توجد سجلات مسح محفوظة لتوليد استمارة PDF."
                                } else {
                                    val domainRecords = realEntities.map { it.toDomainModel() }
                                    val engine = PdfStampingEngine(context)
                                    val targetRecord = domainRecords.firstOrNull { it.surveyType == SurveyType.WELL } ?: domainRecords.first()
                                    val res = withContext(Dispatchers.IO) {
                                        engine.stampSurveyToPdf(targetRecord)
                                    }
                                    exportSuccessMessage = "تم توليد وختم الاستمارة الرسمية PDF بنجاح: ${res.fileName}"
                                }
                            } catch (e: Exception) {
                                exportSuccessMessage = "خطأ أثناء توليد PDF: ${e.message}"
                            } finally {
                                isExportingPdf = false
                            }
                        }
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
