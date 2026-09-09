package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.presentation.theme.*
import com.yemen.watersurvey.presentation.viewmodel.SurveyViewModel

/**
 * RecordsManagerScreen — complete record lifecycle management screen.
 *
 * Filters: All (الكل), Draft (مسودات), Completed (مكتملة), Packaged (محزومة)
 * Actions per record: Preview (استعراض), Edit (تعديل), Send to Package (إرسال إلى حزمة)
 * PENDING badge ("بانتظار الترقيم") clearly displayed, non-blocking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsManagerScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPreview: (surveyUUID: String) -> Unit = {},
    onNavigateToEdit: (surveyType: String, surveyUUID: String) -> Unit = { _, _ -> },
    viewModel: SurveyViewModel = viewModel()
) {
    val context = LocalContext.current
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val surveyDao = remember { database.surveyRecordDao() }
    val allRecords by surveyDao.getAllSurveys().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("الكل", "مسودات", "مكتملة", "محزومة")

    val filteredRecords = remember(allRecords, selectedTab) {
        when (selectedTab) {
            1 -> allRecords.filter { it.workflowStatus == "DRAFT" }
            2 -> allRecords.filter { it.workflowStatus == "COMPLETED" }
            3 -> allRecords.filter { it.workflowStatus == "PACKAGED" }
            else -> allRecords
        }
    }

    var packagingSurveyUUID by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إدارة السجلات الميدانية",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header stats card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "إجمالي السجلات الميدانية",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${allRecords.size} سجل",
                            color = Emerald400,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatMiniBadge("مسودة", allRecords.count { it.workflowStatus == "DRAFT" }, Amber400)
                        StatMiniBadge("مكتمل", allRecords.count { it.workflowStatus == "COMPLETED" }, Emerald400)
                        StatMiniBadge("محزوم", allRecords.count { it.workflowStatus == "PACKAGED" }, Sky400)
                    }
                }
            }

            // Notification Banner
            snackbarMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Emerald950,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Emerald500)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, color = Emerald300, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        IconButton(onClick = { snackbarMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Filter Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Slate900,
                contentColor = Emerald400,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Emerald400
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    val count = when (index) {
                        1 -> allRecords.count { it.workflowStatus == "DRAFT" }
                        2 -> allRecords.count { it.workflowStatus == "COMPLETED" }
                        3 -> allRecords.count { it.workflowStatus == "PACKAGED" }
                        else -> allRecords.size
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = "$title ($count)",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Emerald400 else Slate400
                            )
                        }
                    )
                }
            }

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = Slate600,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "لا توجد سجلات في هذا التصنيف",
                            color = Slate400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredRecords, key = { it.surveyUUID }) { record ->
                        SurveyRecordItemCard(
                            record = record,
                            isPackaging = packagingSurveyUUID == record.surveyUUID,
                            onPreview = { onNavigateToPreview(record.surveyUUID) },
                            onEdit = { onNavigateToEdit(record.surveyType, record.surveyUUID) },
                            onSendToPackage = {
                                packagingSurveyUUID = record.surveyUUID
                                viewModel.packageRecord(record.surveyUUID) { success, packageId ->
                                    packagingSurveyUUID = null
                                    if (success) {
                                        snackbarMessage = "تمت إضافة السجل بنجاح إلى الحزمة $packageId"
                                    } else {
                                        snackbarMessage = "فشل في تحزيم السجل"
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMiniBadge(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slate800,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.3f)))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(count.toString(), color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Slate400, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SurveyRecordItemCard(
    record: SurveyRecordEntity,
    isPackaging: Boolean,
    onPreview: () -> Unit,
    onEdit: () -> Unit,
    onSendToPackage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val surveyTypeNameAr = when (record.surveyType) {
        "WELL" -> "بئر مياه"
        "SPRING" -> "عين / ينبوع"
        "DAM" -> "سد / حاجز"
        else -> record.surveyType
    }

    val locationSummary = listOfNotNull(
        record.governorateNameSnapshotAr.ifBlank { null },
        record.districtNameSnapshotAr.ifBlank { null },
        record.subDistrictNameSnapshotAr.ifBlank { null },
        record.villageNameSnapshotAr?.ifBlank { null }
    ).joinToString(" - ")

    val isPending = record.registryCode.isBlank() || record.isRegistryCodePending

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Registry / Pending status & Type / Workflow Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPending) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Amber400.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Amber400.copy(alpha = 0.4f)))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("بانتظار الترقيم", color = Amber400, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        text = record.registryCode,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SurveyTypeBadge(record.surveyType, surveyTypeNameAr)
                    WorkflowStatusBadge(record.workflowStatus)
                }
            }

            Text(
                text = "المعرف: ${record.recordId}",
                color = Slate500,
                fontSize = 10.sp
            )

            HorizontalDivider(color = Slate800)

            // Location row
            if (locationSummary.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = locationSummary,
                        color = Slate200,
                        fontSize = 12.sp
                    )
                }
            }

            // GPS & Accuracy Row
            if (record.latitude != null && record.longitude != null) {
                val accStr = record.accuracyM?.let { " (دقة: ${"%.1f".format(it)}م)" } ?: ""
                Text(
                    text = "GPS: ${"%.5f".format(record.latitude)}°, ${"%.5f".format(record.longitude)}°$accStr",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            // Creation Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = record.createdAt,
                        color = Slate500,
                        fontSize = 10.sp
                    )
                }

                if (record.enumeratorCode.isNotBlank()) {
                    Text(
                        text = "المُعدِّد: ${record.enumeratorCode}",
                        color = Slate500,
                        fontSize = 10.sp
                    )
                }
            }

            HorizontalDivider(color = Slate800)

            // Action Buttons Row: Preview, Edit, Send to Package
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Preview Button
                OutlinedButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan400),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Cyan400.copy(alpha = 0.5f))),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("استعراض", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Edit Button
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Amber400.copy(alpha = 0.5f))),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تعديل", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                // Send to Package Button (available for DRAFT or COMPLETED records)
                if (record.workflowStatus != "PACKAGED") {
                    Button(
                        onClick = onSendToPackage,
                        modifier = Modifier.weight(1.2f).height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isPackaging,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        if (isPackaging) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Slate100)
                        } else {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إرسال للحزمة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyTypeBadge(type: String, label: String) {
    val color = when (type) {
        "WELL" -> Cyan400
        "SPRING" -> Sky400
        "DAM" -> Amber400
        else -> Slate400
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slate800,
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.4f)))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
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
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

