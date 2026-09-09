package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.presentation.theme.*
import org.json.JSONObject

/**
 * SurveyPreviewScreen — single unified read-only preview for WELL, SPRING, and DAM records.
 * Reads directly from the database by surveyUUID. Not a PDF; native Compose rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyPreviewScreen(
    surveyUUID: String,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { SurveyAppDatabase.getInstance(context) }
    var record by remember { mutableStateOf<SurveyRecordEntity?>(null) }

    LaunchedEffect(surveyUUID) {
        record = database.surveyRecordDao().getSurveyByUUID(surveyUUID)
    }

    val attachments by produceState<List<com.yemen.watersurvey.data.entity.SurveyAttachmentEntity>>(initialValue = emptyList(), key1 = surveyUUID) {
        value = database.surveyAttachmentDao().getAttachmentsForSurvey(surveyUUID)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("استعراض السجل الميداني", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900, titleContentColor = Slate100, navigationIconContentColor = Slate100
                )
            )
        },
        containerColor = Slate950
    ) { padding ->
        val rec = record
        if (rec == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald400)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header card ──
                PreviewCard(title = null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val registryDisplay = if (rec.registryCode.isBlank() || rec.isRegistryCodePending)
                                "بانتظار الترقيم" else rec.registryCode
                            Text(registryDisplay, color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(rec.recordId, color = Slate500, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SurveyTypeBadge(rec.surveyType)
                            WorkflowStatusBadge(rec.workflowStatus)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Slate800)
                    Spacer(Modifier.height(8.dp))
                    PreviewRow("تاريخ الإنشاء", rec.createdAt)
                    PreviewRow("آخر تحديث", rec.updatedAt)
                    PreviewRow("المُعدِّد", rec.enumeratorCode.ifBlank { rec.enumeratorUsername })
                }

                // ── Administrative location ──
                PreviewCard(title = "الموقع الإداري") {
                    PreviewRow("المحافظة", rec.governorateNameSnapshotAr)
                    PreviewRow("المديرية", rec.districtNameSnapshotAr)
                    PreviewRow("العزلة", rec.subDistrictNameSnapshotAr)
                    rec.villageNameSnapshotAr?.let { PreviewRow("القرية", it) }
                    PreviewRow("P-Code 1", rec.admin1Pcode)
                    PreviewRow("P-Code 2", rec.admin2Pcode)
                    PreviewRow("P-Code 3", rec.admin3Pcode)
                }

                // ── GPS ──
                if (rec.latitude != null && rec.longitude != null) {
                    PreviewCard(title = "إحداثيات GPS") {
                        PreviewRow("خط العرض", "%.6f°".format(rec.latitude))
                        PreviewRow("خط الطول", "%.6f°".format(rec.longitude))
                        rec.altitudeM?.let { PreviewRow("الارتفاع", "%.1f م".format(it)) }
                        rec.accuracyM?.let { PreviewRow("الدقة", "%.1f م".format(it)) }
                        rec.gpsQuality?.let { PreviewRow("جودة الإشارة", it) }
                        rec.gpsCapturedAt?.let { PreviewRow("وقت الالتقاط", it) }
                    }
                }

                // ── Survey type-specific details ──
                when (rec.surveyType) {
                    "WELL" -> {
                        val details = remember(rec.wellDetailsJson) {
                            rec.wellDetailsJson?.let {
                                try { JSONObject(it) } catch (_: Exception) { null }
                            }
                        }
                        if (details != null) {
                            PreviewCard(title = "بيانات البئر الفنية") {
                                PreviewRow("اسم البئر", details.optString("wellNameAr", "-"))
                                PreviewRow("نوع البئر", details.optString("wellType", "-"))
                                PreviewRow("عمق البئر (م)", details.optDouble("wellDepthM", 0.0).toString())
                                PreviewRow("آلية الضخ", details.optString("pumpingMechanism", "-"))
                                PreviewRow("الحالة التشغيلية", details.optString("operationalStatus", "-"))
                            }
                        }
                    }
                    "SPRING" -> {
                        val details = remember(rec.springDetailsJson) {
                            rec.springDetailsJson?.let {
                                try { JSONObject(it) } catch (_: Exception) { null }
                            }
                        }
                        if (details != null) {
                            PreviewCard(title = "بيانات العين / الينبوع") {
                                PreviewRow("اسم العين", details.optString("springNameAr", "-"))
                                PreviewRow("معدل التدفق (ل/ث)", details.optDouble("flowRateLps", 0.0).toString())
                                PreviewRow("نقاء المياه", details.optString("waterClarity", "-"))
                                PreviewRow("الموسمية", details.optString("dischargeSeasonality", "-"))
                            }
                        }
                    }
                    "DAM" -> {
                        val details = remember(rec.damDetailsJson) {
                            rec.damDetailsJson?.let {
                                try { JSONObject(it) } catch (_: Exception) { null }
                            }
                        }
                        if (details != null) {
                            PreviewCard(title = "بيانات السد / الحاجز") {
                                PreviewRow("اسم السد", details.optString("damNameAr", "-"))
                                PreviewRow("نوع المنشأة", details.optString("structureType", "-"))
                                PreviewRow("السعة التخزينية (م³)", details.optDouble("storageCapacityM3", 0.0).toString())
                                PreviewRow("ارتفاع السد (م)", details.optDouble("damHeightM", 0.0).toString())
                                PreviewRow("الحالة الإنشائية", details.optString("structuralCondition", "-"))
                            }
                        }
                    }
                }

                // ── Photos & Attachments ──
                if (attachments.isNotEmpty()) {
                    PreviewCard(title = "الصور الميدانية المرفقة (${attachments.size})") {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(attachments.size) { index ->
                                AttachmentThumbnail(attachments[index])
                            }
                        }
                    }
                }

                // ── UUID footer ──
                Text(
                    "UUID: ${rec.surveyUUID}",
                    color = Slate700, fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PreviewCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Slate800)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, color = Emerald400, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                HorizontalDivider(color = Slate800, modifier = Modifier.padding(bottom = 4.dp))
            }
            content()
        }
    }
}

@Composable
private fun PreviewRow(label: String, value: String) {
    if (value.isBlank() || value == "0.0" || value == "-") return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = Slate400, fontSize = 12.sp, modifier = Modifier.weight(0.4f))
        Text(value, color = Slate100, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun SurveyTypeBadge(surveyType: String) {
    val (label, color) = when (surveyType) {
        "WELL" -> "بئر" to Cyan400
        "SPRING" -> "عين" to Sky400
        "DAM" -> "سد" to Amber400
        else -> surveyType to Slate400
    }
    Surface(shape = RoundedCornerShape(6.dp), color = Slate800) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun WorkflowStatusBadge(status: String) {
    val (label, color) = when (status) {
        "DRAFT" -> "مسودة" to Amber400
        "COMPLETED" -> "مكتمل" to Emerald400
        "PACKAGED" -> "في حزمة" to Sky400
        else -> status to Slate400
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
