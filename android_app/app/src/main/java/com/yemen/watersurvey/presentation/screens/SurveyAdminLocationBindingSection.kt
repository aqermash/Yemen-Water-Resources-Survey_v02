package com.yemen.watersurvey.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.admin.AdminCascadingSelector
import com.yemen.watersurvey.core.admin.GpsAdministrativeResolver
import com.yemen.watersurvey.data.entity.*
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.launch

/**
 * Phase 11 Administrative Reference Integration & GPS Location Resolution UI Component.
 *
 * Implements:
 * 1. Cascading selection: Admin1 (المحافظة) -> Admin2 (المديرية) -> Admin3 (العزلة) -> Village (القرية).
 * 2. Controlled Local Name Override (تسجيل مسمى محلي بديل مع توثيق السبب).
 * 3. Real-time offline spatial verification using Point-in-Polygon (PIP) raycasting against loaded geometry.
 * 4. Clear visual feedback for LOCATION_MATCH and LOCATION_MISMATCH (non-destructive; manual choice is never overwritten automatically).
 * 5. Generation of immutable AdministrativeLocationSnapshot for survey record binding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyAdminLocationBindingSection(
    selector: AdminCascadingSelector,
    resolver: GpsAdministrativeResolver,
    currentGpsLocation: GpsLocationResult?,
    initialAdmin1Pcode: String = "",
    initialAdmin2Pcode: String = "",
    initialAdmin3Pcode: String = "",
    initialVillageRefId: String? = null,
    initialCustomVillageName: String? = null,
    initialIsOverride: Boolean = false,
    initialOverrideReason: String? = null,
    onAdministrativeIdentityChanged: (
        admin1Pcode: String,
        admin2Pcode: String,
        admin3Pcode: String,
        villageRefId: String?,
        snapshot: AdministrativeLocationSnapshot,
        isOverride: Boolean,
        overrideReason: String?,
        resolutionStatus: AdminResolutionStatus,
        resolvedLocation: ResolvedAdministrativeLocation?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    // Administrative Lists
    var admin1List by remember { mutableStateOf<List<Admin1Entity>>(emptyList()) }
    var admin2List by remember { mutableStateOf<List<Admin2Entity>>(emptyList()) }
    var admin3List by remember { mutableStateOf<List<Admin3Entity>>(emptyList()) }
    var villageList by remember { mutableStateOf<List<VillageEntity>>(emptyList()) }

    // Selected Administrative States
    var selectedAdmin1 by remember { mutableStateOf(initialAdmin1Pcode) }
    var selectedAdmin2 by remember { mutableStateOf(initialAdmin2Pcode) }
    var selectedAdmin3 by remember { mutableStateOf(initialAdmin3Pcode) }
    var selectedVillageId by remember { mutableStateOf(initialVillageRefId) }

    // Local Override States
    var isLocalOverride by remember { mutableStateOf(initialIsOverride) }
    var customVillageName by remember { mutableStateOf(initialCustomVillageName ?: "") }
    var overrideReason by remember { mutableStateOf(initialOverrideReason ?: "") }

    // GPS Resolution & Consistency States
    var isResolvingGps by remember { mutableStateOf(false) }
    var resolvedGpsLocation by remember { mutableStateOf<ResolvedAdministrativeLocation?>(null) }
    var consistencyResult by remember { mutableStateOf<GpsAdministrativeResolver.SelectionConsistencyResult?>(null) }

    // Dropdown expansion states
    var expandedAdmin1 by remember { mutableStateOf(false) }
    var expandedAdmin2 by remember { mutableStateOf(false) }
    var expandedAdmin3 by remember { mutableStateOf(false) }
    var expandedVillage by remember { mutableStateOf(false) }

    // Load initial Admin1 list
    LaunchedEffect(Unit) {
        admin1List = selector.getGovernorates()
        if (selectedAdmin1.isNotBlank()) {
            admin2List = selector.getDistricts(selectedAdmin1)
        }
        if (selectedAdmin2.isNotBlank()) {
            admin3List = selector.getSubDistricts(selectedAdmin2)
        }
        if (selectedAdmin3.isNotBlank()) {
            villageList = selector.getVillages(selectedAdmin3)
        }
    }

    // Function to re-evaluate and emit snapshot
    fun updateAndEmitBinding() {
        coroutineScope.launch {
            val snapshot = selector.createAdministrativeSnapshot(
                admin1Pcode = selectedAdmin1,
                admin2Pcode = selectedAdmin2,
                admin3Pcode = selectedAdmin3,
                villageReferenceId = selectedVillageId,
                customVillageNameAr = if (isLocalOverride) customVillageName else null,
                isLocalOverride = isLocalOverride,
                localOverrideId = if (isLocalOverride) "OVR-${System.currentTimeMillis()}" else null
            )

            val status = consistencyResult?.status ?: AdminResolutionStatus.NOT_EVALUATED

            onAdministrativeIdentityChanged(
                selectedAdmin1,
                selectedAdmin2,
                selectedAdmin3,
                selectedVillageId,
                snapshot,
                isLocalOverride,
                if (isLocalOverride) overrideReason else null,
                status,
                resolvedGpsLocation
            )
        }
    }

    // Trigger GPS Spatial Verification
    fun performSpatialVerification() {
        if (currentGpsLocation == null) {
            consistencyResult = GpsAdministrativeResolver.SelectionConsistencyResult(
                status = AdminResolutionStatus.NO_GPS_FIX,
                isConsistent = false,
                detailsAr = "إحداثيات GPS غير متوفرة حالياً لتنفيذ التحقق المكاني.",
                resolvedLocation = null
            )
            updateAndEmitBinding()
            return
        }

        coroutineScope.launch {
            isResolvingGps = true
            try {
                val resolved = resolver.resolveAdministrativeLocation(
                    latitude = currentGpsLocation.latitude,
                    longitude = currentGpsLocation.longitude,
                    gpsAccuracyM = currentGpsLocation.accuracyM
                )
                resolvedGpsLocation = resolved

                val consistency = resolver.evaluateManualSelectionConsistency(
                    selectedAdmin1Pcode = selectedAdmin1,
                    selectedAdmin2Pcode = selectedAdmin2,
                    selectedAdmin3Pcode = selectedAdmin3,
                    latitude = currentGpsLocation.latitude,
                    longitude = currentGpsLocation.longitude,
                    gpsAccuracyM = currentGpsLocation.accuracyM
                )
                consistencyResult = consistency
                updateAndEmitBinding()
            } catch (e: Exception) {
                consistencyResult = GpsAdministrativeResolver.SelectionConsistencyResult(
                    status = AdminResolutionStatus.NOT_EVALUATED,
                    isConsistent = false,
                    detailsAr = "حدث خطأ أثناء إجراء التحقق المكاني: ${e.message}",
                    resolvedLocation = null
                )
            } finally {
                isResolvingGps = false
            }
        }
    }

    // Auto-verify if GPS arrives or changes
    LaunchedEffect(currentGpsLocation, selectedAdmin1, selectedAdmin2, selectedAdmin3) {
        if (currentGpsLocation != null && selectedAdmin1.isNotBlank()) {
            performSpatialVerification()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "المرجع الإداري والتحقق المكاني (OCHA/GIS)",
                            color = Slate100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ربط وتوثيق الهوية الإدارية الرسمية مع التحقق من إحداثيات WGS84 GPS",
                            color = Slate400,
                            fontSize = 11.sp
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { performSpatialVerification() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Slate800,
                        contentColor = Emerald400
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isResolvingGps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Emerald400,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(text = "تحقق مكاني", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = Slate800, thickness = 0.75.dp)

            // 1. Governorate (Admin1) Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "1. المحافظة (Governorate - Admin1) *",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ExposedDropdownMenuBox(
                    expanded = expandedAdmin1,
                    onExpandedChange = { expandedAdmin1 = !expandedAdmin1 }
                ) {
                    val currentGov = admin1List.find { it.admin1Pcode == selectedAdmin1 }
                    OutlinedTextField(
                        value = if (currentGov != null) "${currentGov.nameAr} (${currentGov.admin1Pcode})" else "اختر المحافظة...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAdmin1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate200,
                            focusedContainerColor = Slate950,
                            unfocusedContainerColor = Slate950
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAdmin1,
                        onDismissRequest = { expandedAdmin1 = false },
                        modifier = Modifier.background(Slate900)
                    ) {
                        admin1List.forEach { gov ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(gov.nameAr, color = Slate100, fontWeight = FontWeight.Medium)
                                        Text(gov.admin1Pcode, color = Slate400, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    selectedAdmin1 = gov.admin1Pcode
                                    selectedAdmin2 = ""
                                    selectedAdmin3 = ""
                                    selectedVillageId = null
                                    expandedAdmin1 = false
                                    coroutineScope.launch {
                                        admin2List = selector.getDistricts(gov.admin1Pcode)
                                        admin3List = emptyList()
                                        villageList = emptyList()
                                        updateAndEmitBinding()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 2. District (Admin2) Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "2. المديرية (District - Admin2) *",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ExposedDropdownMenuBox(
                    expanded = expandedAdmin2 && selectedAdmin1.isNotBlank(),
                    onExpandedChange = { if (selectedAdmin1.isNotBlank()) expandedAdmin2 = !expandedAdmin2 }
                ) {
                    val currentDist = admin2List.find { it.admin2Pcode == selectedAdmin2 }
                    OutlinedTextField(
                        value = if (currentDist != null) "${currentDist.nameAr} (${currentDist.admin2Pcode})" else if (selectedAdmin1.isBlank()) "يرجى اختيار المحافظة أولاً" else "اختر المديرية...",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedAdmin1.isNotBlank(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAdmin2) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate200,
                            focusedContainerColor = Slate950,
                            unfocusedContainerColor = Slate950
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAdmin2 && selectedAdmin1.isNotBlank(),
                        onDismissRequest = { expandedAdmin2 = false },
                        modifier = Modifier.background(Slate900)
                    ) {
                        admin2List.forEach { dist ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(dist.nameAr, color = Slate100, fontWeight = FontWeight.Medium)
                                        Text(dist.admin2Pcode, color = Slate400, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    selectedAdmin2 = dist.admin2Pcode
                                    selectedAdmin3 = ""
                                    selectedVillageId = null
                                    expandedAdmin2 = false
                                    coroutineScope.launch {
                                        admin3List = selector.getSubDistricts(dist.admin2Pcode)
                                        villageList = emptyList()
                                        updateAndEmitBinding()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. Sub-district (Admin3 / العزلة) Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "3. العزلة (Sub-district - Admin3) *",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ExposedDropdownMenuBox(
                    expanded = expandedAdmin3 && selectedAdmin2.isNotBlank(),
                    onExpandedChange = { if (selectedAdmin2.isNotBlank()) expandedAdmin3 = !expandedAdmin3 }
                ) {
                    val currentUzlah = admin3List.find { it.admin3Pcode == selectedAdmin3 }
                    OutlinedTextField(
                        value = if (currentUzlah != null) "${currentUzlah.nameAr} (${currentUzlah.admin3Pcode})" else if (selectedAdmin2.isBlank()) "يرجى اختيار المديرية أولاً" else "اختر العزلة...",
                        onValueChange = {},
                        readOnly = true,
                        enabled = selectedAdmin2.isNotBlank(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAdmin3) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Emerald500,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Slate100,
                            unfocusedTextColor = Slate200,
                            focusedContainerColor = Slate950,
                            unfocusedContainerColor = Slate950
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expandedAdmin3 && selectedAdmin2.isNotBlank(),
                        onDismissRequest = { expandedAdmin3 = false },
                        modifier = Modifier.background(Slate900)
                    ) {
                        admin3List.forEach { uzlah ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(uzlah.nameAr, color = Slate100, fontWeight = FontWeight.Medium)
                                        Text(uzlah.admin3Pcode, color = Slate400, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    selectedAdmin3 = uzlah.admin3Pcode
                                    selectedVillageId = null
                                    expandedAdmin3 = false
                                    coroutineScope.launch {
                                        villageList = selector.getVillages(uzlah.admin3Pcode)
                                        updateAndEmitBinding()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 4. Village / Local Name Override Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4. القرية / المحلة (Village / Locality)",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isLocalOverride) "مسمى محلي بديل" else "قائمة معتمدة",
                            color = if (isLocalOverride) Amber400 else Slate400,
                            fontSize = 11.sp
                        )
                        Switch(
                            checked = isLocalOverride,
                            onCheckedChange = {
                                isLocalOverride = it
                                updateAndEmitBinding()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Amber400,
                                checkedTrackColor = Amber900,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate800
                            )
                        )
                    }
                }

                if (!isLocalOverride) {
                    ExposedDropdownMenuBox(
                        expanded = expandedVillage && selectedAdmin3.isNotBlank(),
                        onExpandedChange = { if (selectedAdmin3.isNotBlank()) expandedVillage = !expandedVillage }
                    ) {
                        val currentVil = villageList.find { it.villageId == selectedVillageId }
                        OutlinedTextField(
                            value = if (currentVil != null) currentVil.nameAr else if (selectedAdmin3.isBlank()) "يرجى اختيار العزلة أولاً" else if (villageList.isEmpty()) "لا توجد قرى مسجلة (يمكنك تفعيل المسمى المحلي)" else "اختر القرية المعتمدة...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = selectedAdmin3.isNotBlank() && villageList.isNotEmpty(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVillage) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200,
                                focusedContainerColor = Slate950,
                                unfocusedContainerColor = Slate950
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedVillage && selectedAdmin3.isNotBlank() && villageList.isNotEmpty(),
                            onDismissRequest = { expandedVillage = false },
                            modifier = Modifier.background(Slate900)
                        ) {
                            villageList.forEach { vil ->
                                DropdownMenuItem(
                                    text = {
                                        Text(vil.nameAr, color = Slate100, fontWeight = FontWeight.Medium)
                                    },
                                    onClick = {
                                        selectedVillageId = vil.villageId
                                        expandedVillage = false
                                        updateAndEmitBinding()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Local Name Override Inputs
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customVillageName,
                            onValueChange = {
                                customVillageName = it
                                updateAndEmitBinding()
                            },
                            label = { Text("المسمى المحلي للقرية / المحلة باللغة العربية *") },
                            placeholder = { Text("مثال: قرية الدرب، محلة شعب الحصن...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200,
                                focusedContainerColor = Slate950,
                                unfocusedContainerColor = Slate950
                            )
                        )

                        OutlinedTextField(
                            value = overrideReason,
                            onValueChange = {
                                overrideReason = it
                                updateAndEmitBinding()
                            },
                            label = { Text("مبرر استخدام التسمية المحلية (التوثيق الميداني)") },
                            placeholder = { Text("مثال: التسمية الشائعة لدى الأهالي ولم ترد في المسرد الرسمي...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Amber400,
                                unfocusedBorderColor = Slate700,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate200,
                                focusedContainerColor = Slate950,
                                unfocusedContainerColor = Slate950
                            )
                        )
                    }
                }
            }

            // 5. GPS Spatial Verification Results Banner
            consistencyResult?.let { result ->
                val (bannerBg, bannerBorder, bannerIcon, bannerTitleColor) = when (result.status) {
                    AdminResolutionStatus.LOCATION_MATCH -> Quadruple(
                        Emerald950.copy(alpha = 0.4f),
                        Emerald600,
                        Icons.Default.CheckCircle,
                        Emerald400
                    )
                    AdminResolutionStatus.LOCATION_MISMATCH -> Quadruple(
                        Red950.copy(alpha = 0.5f),
                        Red600,
                        Icons.Default.Warning,
                        Red400
                    )
                    AdminResolutionStatus.AMBIGUOUS_BOUNDARY, AdminResolutionStatus.OUTSIDE_COVERAGE -> Quadruple(
                        Amber950.copy(alpha = 0.4f),
                        Amber600,
                        Icons.Default.Info,
                        Amber400
                    )
                    AdminResolutionStatus.NO_GPS_FIX, AdminResolutionStatus.NOT_EVALUATED -> Quadruple(
                        Slate800.copy(alpha = 0.5f),
                        Slate700,
                        Icons.Default.LocationOff,
                        Slate300
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = bannerIcon,
                                contentDescription = null,
                                tint = bannerTitleColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "حالة التحقق المكاني: ${result.status.titleAr}",
                                color = bannerTitleColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = result.detailsAr,
                            color = Slate200,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )

                        // If Mismatch: Show comparison table and action button
                        if (result.status == AdminResolutionStatus.LOCATION_MISMATCH && result.resolvedLocation != null) {
                            val res = result.resolvedLocation
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Slate950.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "الموقع المكتشف فضائياً عبر إحداثيات GPS:",
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "المحافظة: ${res.admin1Pcode} | المديرية: ${res.admin2Pcode ?: "غير محدد"} | العزلة: ${res.admin3Pcode ?: "غير محدد"}",
                                    color = Emerald400,
                                    fontSize = 11.sp
                                )

                                Button(
                                    onClick = {
                                        selectedAdmin1 = res.admin1Pcode
                                        res.admin2Pcode?.let { selectedAdmin2 = it }
                                        res.admin3Pcode?.let { selectedAdmin3 = it }
                                        coroutineScope.launch {
                                            admin2List = selector.getDistricts(selectedAdmin1)
                                            if (selectedAdmin2.isNotBlank()) {
                                                admin3List = selector.getSubDistricts(selectedAdmin2)
                                            }
                                            if (selectedAdmin3.isNotBlank()) {
                                                villageList = selector.getVillages(selectedAdmin3)
                                            }
                                            performSpatialVerification()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Slate800,
                                        contentColor = Slate100
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "اعتماد وتطبيق الموقع المقترح من الـ GPS",
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Nearest village information if available
                        result.resolvedLocation?.nearestVillageNameAr?.let { nearestName ->
                            val dist = result.resolvedLocation.distanceToNearestVillageM
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "أقرب قرية مسجلة: $nearestName ${dist?.let { "(على بعد ${it.toInt()} م)" } ?: ""}",
                                    color = Slate300,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
