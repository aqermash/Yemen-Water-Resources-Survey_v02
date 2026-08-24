package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.data.entity.SurveyRecordEntity
import com.yemen.watersurvey.presentation.theme.*

/**
 * Real Records Manager Screen displaying saved field survey records from Room database.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsManagerScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val surveyDao = remember { database.surveyRecordDao() }
    val records by surveyDao.getAllSurveys().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إدارة السجلات الميدانية (Records Manager)",
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
                .padding(16.dp)
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
                            text = "إجمالي السجلات المحفوظة",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${records.size} سجل",
                            color = Emerald400,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Emerald950)
                            .border(1.dp, Emerald500.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = Emerald400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (records.isEmpty()) {
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
                            text = "لا توجد سجلات مسح محفوظة حالياً",
                            color = Slate400,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "قم بإنشاء مسح جديد عبر لوحة التحكم",
                            color = Slate600,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(records) { record ->
                        SurveyRecordItemCard(record = record)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyRecordItemCard(
    record: SurveyRecordEntity,
    modifier: Modifier = Modifier
) {
    val surveyTypeNameAr = when (record.surveyType) {
        "WELL" -> "بئر مياه (Well)"
        "SPRING" -> "عين مياه (Spring)"
        "DAM" -> "سد / حاجز (Dam)"
        else -> record.surveyType
    }

    val locationSummary = listOfNotNull(
        record.governorateNameSnapshotAr.ifBlank { null },
        record.districtNameSnapshotAr.ifBlank { null },
        record.subDistrictNameSnapshotAr.ifBlank { null },
        record.villageNameSnapshotAr?.ifBlank { null }
    ).joinToString(" - ")

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Registry Code & Type Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.registryCode.ifBlank { record.recordId },
                    color = Emerald400,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Emerald950,
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Emerald500.copy(alpha = 0.5f)))
                ) {
                    Text(
                        text = surveyTypeNameAr,
                        color = Emerald300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

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
                        text = record.enumeratorCode,
                        color = Slate500,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
