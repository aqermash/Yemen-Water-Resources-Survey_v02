package com.yemen.watersurvey.presentation.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yemen.watersurvey.presentation.viewmodel.GpsCaptureViewModel
import com.yemen.watersurvey.core.admin.AdminCascadingSelector
import com.yemen.watersurvey.core.admin.GpsAdministrativeResolver
import com.yemen.watersurvey.core.location.GpsCaptureEvent
import com.yemen.watersurvey.core.location.GpsCaptureState
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

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
    initialSnapshot: AdministrativeLocationSnapshot? = null,
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
        resolvedLocation: ResolvedAdministrativeLocation?,
        gpsLocation: GpsLocationResult?
    ) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GpsCaptureViewModel = viewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val gpsState by viewModel.state.collectAsState()

    var admin1List by remember { mutableStateOf<List<Admin1Governorate>>(emptyList()) }
    var admin2List by remember { mutableStateOf<List<Admin2District>>(emptyList()) }
    var admin3List by remember { mutableStateOf<List<Admin3Uzlah>>(emptyList()) }
    var villageList by remember { mutableStateOf<List<AdminVillage>>(emptyList()) }

    var selectedAdmin1 by remember { mutableStateOf(initialAdmin1Pcode) }
    var selectedAdmin2 by remember { mutableStateOf(initialAdmin2Pcode) }
    var selectedAdmin3 by remember { mutableStateOf(initialAdmin3Pcode) }
    var selectedVillageId by remember { mutableStateOf(initialVillageRefId) }

    var isLocalOverride by remember { mutableStateOf(initialIsOverride) }
    var customVillageName by remember { mutableStateOf(initialCustomVillageName ?: "") }
    var overrideReason by remember { mutableStateOf(initialOverrideReason ?: "") }

    var isResolvingGps by remember { mutableStateOf(false) }
    var resolvedGpsLocation by remember { mutableStateOf<ResolvedAdministrativeLocation?>(null) }
    var consistencyResult by remember { mutableStateOf<Pair<AdminResolutionStatus, String>?>(null) }

    var expandedAdmin1 by remember { mutableStateOf(false) }
    var expandedAdmin2 by remember { mutableStateOf(false) }
    var expandedAdmin3 by remember { mutableStateOf(false) }
    var expandedVillage by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        viewModel.onPermissionResult(granted)
    }

    fun doStartGpsCapture() {
        if (!gpsState.hasPermission) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }
        viewModel.onEvent(GpsCaptureEvent.StartCapture)
    }

    fun stopGpsCapture() {
        viewModel.onEvent(GpsCaptureEvent.StopCapture)
    }

    val liveGpsLocation = gpsState.currentLocation

    fun updateAndEmitBinding() {
        val hasAnySelection = selectedAdmin1.isNotBlank() || selectedAdmin2.isNotBlank() || selectedAdmin3.isNotBlank() || !selectedVillageId.isNullOrBlank() || isLocalOverride || initialSnapshot != null
        if (!hasAnySelection) {
            return
        }

        coroutineScope.launch {
            val newSnapshot = selector.createAdministrativeSnapshot(
                admin1Pcode = selectedAdmin1,
                admin2Pcode = selectedAdmin2,
                admin3Pcode = selectedAdmin3,
                villageReferenceId = selectedVillageId,
                customVillageNameAr = if (isLocalOverride) customVillageName else null,
                isLocalNameOverride = isLocalOverride,
                localOverrideId = if (isLocalOverride) "OVR-${System.currentTimeMillis()}" else null
            )

            val finalSnapshot = AdministrativeLocationSnapshot(
                admin1Pcode = newSnapshot.admin1Pcode,
                admin2Pcode = newSnapshot.admin2Pcode,
                admin3Pcode = newSnapshot.admin3Pcode,
                governorateNameAr = newSnapshot.governorateNameAr.ifBlank {
                    if (selectedAdmin1 == initialSnapshot?.admin1Pcode) initialSnapshot.governorateNameAr else ""
                },
                districtNameAr = newSnapshot.districtNameAr.ifBlank {
                    if (selectedAdmin2 == initialSnapshot?.admin2Pcode) initialSnapshot.districtNameAr else ""
                },
                subDistrictNameAr = newSnapshot.subDistrictNameAr.ifBlank {
                    if (selectedAdmin3 == initialSnapshot?.admin3Pcode) initialSnapshot.subDistrictNameAr else ""
                },
                villageNameAr = newSnapshot.villageNameAr ?: if (selectedVillageId == initialVillageRefId) initialSnapshot?.villageNameAr else null,
                localOverrideId = newSnapshot.localOverrideId ?: initialSnapshot?.localOverrideId
            )

            val status = consistencyResult?.first ?: AdminResolutionStatus.NOT_EVALUATED

            onAdministrativeIdentityChanged(
                selectedAdmin1,
                selectedAdmin2,
                selectedAdmin3,
                selectedVillageId,
                finalSnapshot,
                isLocalOverride,
                if (isLocalOverride) overrideReason else null,
                status,
                resolvedGpsLocation,
                liveGpsLocation ?: currentGpsLocation
            )
        }
    }


    fun performSpatialVerification() {
        if (liveGpsLocation == null) {
            consistencyResult = Pair(AdminResolutionStatus.NO_GPS, "إحداثيات GPS غير متوفرة حالياً لتنفيذ التحقق المكاني.")
            updateAndEmitBinding()
            return
        }

        coroutineScope.launch {
            isResolvingGps = true
            try {
                val resolved = resolver.resolveAdministrativeLocation(
                    latitude = liveGpsLocation.latitude,
                    longitude = liveGpsLocation.longitude,
                    accuracyM = liveGpsLocation.accuracyM
                )
                resolvedGpsLocation = resolved

                val consistency = resolver.evaluateManualSelectionConsistency(
                    selectedAdmin1Pcode = selectedAdmin1,
                    selectedAdmin2Pcode = selectedAdmin2,
                    selectedAdmin3Pcode = selectedAdmin3,
                    resolvedLocation = resolved
                )
                consistencyResult = consistency
                updateAndEmitBinding()
            } catch (e: Exception) {
                consistencyResult = Pair(AdminResolutionStatus.NOT_EVALUATED, "حدث خطأ أثناء إجراء التحقق المكاني: ${e.message}")
            } finally {
                isResolvingGps = false
            }
        }
    }

    LaunchedEffect(initialAdmin1Pcode, initialAdmin2Pcode, initialAdmin3Pcode, initialVillageRefId, initialCustomVillageName, initialIsOverride, initialOverrideReason) {
        selectedAdmin1 = initialAdmin1Pcode
        selectedAdmin2 = initialAdmin2Pcode
        selectedAdmin3 = initialAdmin3Pcode
        selectedVillageId = initialVillageRefId
        isLocalOverride = initialIsOverride
        customVillageName = initialCustomVillageName ?: ""
        overrideReason = initialOverrideReason ?: ""

        admin1List = selector.getGovernorates()
        if (initialAdmin1Pcode.isNotBlank()) {
            admin2List = selector.getDistrictsForGovernorate(initialAdmin1Pcode)
        } else {
            admin2List = emptyList()
        }
        if (initialAdmin2Pcode.isNotBlank()) {
            admin3List = selector.getUzlahsForDistrict(initialAdmin2Pcode)
        } else {
            admin3List = emptyList()
        }
        if (initialAdmin3Pcode.isNotBlank()) {
            villageList = selector.getVillagesForUzlah(initialAdmin3Pcode)
        } else {
            villageList = emptyList()
        }

        if (initialAdmin1Pcode.isNotBlank() || initialAdmin2Pcode.isNotBlank() || initialAdmin3Pcode.isNotBlank() || !initialVillageRefId.isNullOrBlank()) {
            updateAndEmitBinding()
        }
    }

    LaunchedEffect(liveGpsLocation, selectedAdmin1, selectedAdmin2, selectedAdmin3) {
        if (liveGpsLocation != null && selectedAdmin1.isNotBlank()) {
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
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = when {
                            gpsState.accuracyEnoughToProceed -> Emerald400
                            gpsState.isCapturing -> Amber400
                            else -> Slate400
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "التقاط GPS وتوثيق الموقع",
                            color = Slate100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = gpsState.statusMessage.ifBlank {
                                if (!gpsState.hasPermission) "الرجاء إعطاء إذن الموقع للمتابعة"
                                else "اضغط زر التقاط GPS للبدء"
                            },
                            color = when {
                                gpsState.accuracyEnoughToProceed -> Emerald400
                                gpsState.isCapturing -> Amber400
                                else -> Slate400
                            },
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gpsState.currentLocation?.let { loc ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%.5f°, %.5f°".format(loc.latitude, loc.longitude),
                                color = Slate300,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "دقة: %.1f م — ${loc.quality.titleAr}".format(loc.accuracyM),
                                color = when (loc.quality) {
                                    GpsAccuracyQuality.EXCELLENT, GpsAccuracyQuality.GOOD -> Emerald400
                                    GpsAccuracyQuality.ACCEPTABLE_WITH_WARNING -> Amber400
                                    else -> Red400
                                },
                                fontSize = 10.sp
                            )
                        }
                    }

                    if (gpsState.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = if (gpsState.accuracyEnoughToProceed) Emerald400 else Amber400,
                            strokeWidth = 2.dp
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            if (gpsState.isCapturing) stopGpsCapture() else doStartGpsCapture()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (gpsState.accuracyEnoughToProceed) Emerald900 else Slate800,
                            contentColor = if (gpsState.accuracyEnoughToProceed) Emerald400 else Slate300
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (gpsState.isCapturing) Icons.Default.Stop else Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (gpsState.isCapturing) "إيقاف" else "التقاط",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!gpsState.accuracyEnoughToProceed && gpsState.currentLocation != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Amber950.copy(alpha = 0.3f))
                        .border(1.dp, Amber700, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "الدقة الحالية (${String.format("%.1f", gpsState.currentLocation?.accuracyM ?: 0f)} م) أقل من الحد المطلوب (${GpsCaptureState.ACCURACY_THRESHOLD_METERS.toInt()} م). انتظر حتى تتحسن.",
                        color = Amber300,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = Slate800, thickness = 0.75.dp)

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
                    val govDisplay = when {
                        currentGov != null -> "${currentGov.nameAr} (${currentGov.admin1Pcode})"
                        selectedAdmin1.isNotBlank() && selectedAdmin1 == initialSnapshot?.admin1Pcode && !initialSnapshot.governorateNameAr.isBlank() -> "${initialSnapshot.governorateNameAr} (${selectedAdmin1})"
                        selectedAdmin1.isNotBlank() -> selectedAdmin1
                        else -> "اختر المحافظة..."
                    }
                    OutlinedTextField(
                        value = govDisplay,
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
                                        admin2List = selector.getDistrictsForGovernorate(gov.admin1Pcode)
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
                    val distDisplay = when {
                        currentDist != null -> "${currentDist.nameAr} (${currentDist.admin2Pcode})"
                        selectedAdmin2.isNotBlank() && selectedAdmin2 == initialSnapshot?.admin2Pcode && !initialSnapshot.districtNameAr.isBlank() -> "${initialSnapshot.districtNameAr} (${selectedAdmin2})"
                        selectedAdmin2.isNotBlank() -> selectedAdmin2
                        selectedAdmin1.isBlank() -> "يرجى اختيار المحافظة أولاً"
                        else -> "اختر المديرية..."
                    }
                    OutlinedTextField(
                        value = distDisplay,
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
                                        admin3List = selector.getUzlahsForDistrict(dist.admin2Pcode)
                                        villageList = emptyList()
                                        updateAndEmitBinding()
                                    }
                                }
                            )
                        }
                    }
                }
            }

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
                    val uzlahDisplay = when {
                        currentUzlah != null -> "${currentUzlah.nameAr} (${currentUzlah.admin3Pcode})"
                        selectedAdmin3.isNotBlank() && selectedAdmin3 == initialSnapshot?.admin3Pcode && !initialSnapshot.subDistrictNameAr.isBlank() -> "${initialSnapshot.subDistrictNameAr} (${selectedAdmin3})"
                        selectedAdmin3.isNotBlank() -> selectedAdmin3
                        selectedAdmin2.isBlank() -> "يرجى اختيار المديرية أولاً"
                        else -> "اختر العزلة..."
                    }
                    OutlinedTextField(
                        value = uzlahDisplay,
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
                                        villageList = selector.getVillagesForUzlah(uzlah.admin3Pcode)
                                        updateAndEmitBinding()
                                    }
                                }
                            )
                        }
                    }
                }
            }

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
                        val vilDisplay = when {
                            currentVil != null -> currentVil.nameAr
                            selectedVillageId != null && selectedVillageId == initialVillageRefId && !initialSnapshot?.villageNameAr.isNullOrBlank() -> initialSnapshot!!.villageNameAr!!
                            selectedAdmin3.isBlank() -> "يرجى اختيار العزلة أولاً"
                            villageList.isEmpty() -> "لا توجد قرى مسجلة (يمكنك تفعيل المسمى المحلي)"
                            else -> "اختر القرية المعتمدة..."
                        }
                        OutlinedTextField(
                            value = vilDisplay,
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

            consistencyResult?.let { result ->
                val status = result.first
                val details = result.second
                val (bannerBg, bannerBorder, bannerIcon, bannerTitleColor) = when (status) {
                    AdminResolutionStatus.CONFIRMED -> Quadruple(
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
                    AdminResolutionStatus.UNRESOLVED, AdminResolutionStatus.OUTSIDE_BOUNDARIES -> Quadruple(
                        Amber950.copy(alpha = 0.4f),
                        Amber600,
                        Icons.Default.Info,
                        Amber400
                    )
                    AdminResolutionStatus.NO_GPS, AdminResolutionStatus.NOT_EVALUATED -> Quadruple(
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
                                text = "حالة التحقق المكاني: ${status.titleAr}",
                                color = bannerTitleColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = details,
                            color = Slate200,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )

                        if (status == AdminResolutionStatus.LOCATION_MISMATCH) {
                            resolvedGpsLocation?.let { res ->
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
                                        text = "المحافظة: ${res.admin1Pcode ?: "غير محدد"} | المديرية: ${res.admin2Pcode ?: "غير محدد"} | العزلة: ${res.admin3Pcode ?: "غير محدد"}",
                                        color = Emerald400,
                                        fontSize = 11.sp
                                    )

                                    Button(
                                        onClick = {
                                            selectedAdmin1 = res.admin1Pcode ?: selectedAdmin1
                                            selectedAdmin2 = res.admin2Pcode ?: selectedAdmin2
                                            selectedAdmin3 = res.admin3Pcode ?: selectedAdmin3
                                            coroutineScope.launch {
                                                admin2List = selector.getDistrictsForGovernorate(selectedAdmin1)
                                                if (selectedAdmin2.isNotBlank()) {
                                                    admin3List = selector.getUzlahsForDistrict(selectedAdmin2)
                                                }
                                                if (selectedAdmin3.isNotBlank()) {
                                                    villageList = selector.getVillagesForUzlah(selectedAdmin3)
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

                                res.nearestVillageNameAr?.let { nearestName ->
                                    val dist = res.distanceToNearestVillageM
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
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
