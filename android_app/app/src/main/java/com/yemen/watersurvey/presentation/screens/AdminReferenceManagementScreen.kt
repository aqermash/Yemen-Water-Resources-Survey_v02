package com.yemen.watersurvey.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.admin.AdminCascadingSelector
import com.yemen.watersurvey.core.admin.AdminReferencePackageManager
import com.yemen.watersurvey.core.admin.AdministrativeOverrideManager
import com.yemen.watersurvey.core.admin.GpsAdministrativeResolver
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.domain.model.*
import kotlinx.coroutines.launch

/**
 * Material 3 Arabic RTL Administrative Reference & GIS Management Screen.
 *
 * Provides:
 * 1. Reference package version inspection (OCHA + Yemen-Info + TopoJSON).
 * 2. Cascading administrative hierarchy browser (Gov -> Dist -> Uzlah -> Village).
 * 3. Controlled local name override proposals and approvals.
 * 4. Unmapped villages / needs review inspector.
 * 5. Offline GPS Point-in-Polygon testing and boundary verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReferenceManagementScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val packageManager = remember { AdminReferencePackageManager(context, database) }
    val cascadingSelector = remember { AdminCascadingSelector(database) }
    val overrideManager = remember { AdministrativeOverrideManager(database) }
    val gpsResolver = remember { GpsAdministrativeResolver(database) }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) } // 0: Hierarchy, 1: Overrides, 2: Unmapped, 3: GPS PIP Tester
    var packageMetadata by remember { mutableStateOf<AdminReferencePackageMetadata?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Hierarchy State
    var governorates by remember { mutableStateOf<List<Admin1Governorate>>(emptyList()) }
    var selectedGovernorate by remember { mutableStateOf<Admin1Governorate?>(null) }
    var districts by remember { mutableStateOf<List<Admin2District>>(emptyList()) }
    var selectedDistrict by remember { mutableStateOf<Admin2District?>(null) }
    var uzlahs by remember { mutableStateOf<List<Admin3Uzlah>>(emptyList()) }
    var selectedUzlah by remember { mutableStateOf<Admin3Uzlah?>(null) }
    var villages by remember { mutableStateOf<List<AdminVillage>>(emptyList()) }

    // Overrides State
    var activeOverrides by remember { mutableStateOf<List<AdministrativeOverride>>(emptyList()) }
    var showAddOverrideDialog by remember { mutableStateOf(false) }

    // Unmapped State
    var unmappedVillages by remember { mutableStateOf<List<AdminVillage>>(emptyList()) }

    // GPS Tester State
    var testLat by remember { mutableStateOf("16.9412") }
    var testLon by remember { mutableStateOf("43.7611") }
    var gpsResolutionResult by remember { mutableStateOf<GpsAdminResolutionResult?>(null) }

    // Search query
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AdminVillage>>(emptyList()) }

    // Initial Data Loader
    LaunchedEffect(Unit) {
        isLoading = true
        // Bootstrap if empty
        val meta = packageManager.getActivePackageMetadata() ?: packageManager.bootstrapCanonicalReferencePackage()
        packageMetadata = meta
        governorates = cascadingSelector.getGovernorates()
        activeOverrides = overrideManager.getAllActiveOverrides()
        unmappedVillages = cascadingSelector.getUnmappedOrReviewVillages(50)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "الدليل الإداري الموحد والحدود الجغرافية (GIS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = packageMetadata?.let { "حزمة: ${it.versionTag} (${it.releaseDate})" } ?: "جاري التحميل...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                packageMetadata = packageManager.bootstrapCanonicalReferencePackage()
                                governorates = cascadingSelector.getGovernorates()
                                activeOverrides = overrideManager.getAllActiveOverrides()
                                unmappedVillages = cascadingSelector.getUnmappedOrReviewVillages(50)
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث البيانات المرجعية")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Version & Provenance KPI Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "المرجعية الرسمية: OCHA / IMMAP (رموز P-Codes)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Surface(
                                color = Color(0xFF1B5E20),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "100% بدون اتصال",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            KpiBadge(title = "المحافظات", count = packageMetadata?.admin1Count ?: 22)
                            KpiBadge(title = "المديريات", count = packageMetadata?.admin2Count ?: 333)
                            KpiBadge(title = "العزل", count = packageMetadata?.admin3Count ?: 2146)
                            KpiBadge(title = "القرى", count = packageMetadata?.villageCount ?: 41494)
                            KpiBadge(title = "تعديلات محلية", count = activeOverrides.size)
                        }
                    }
                }

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الهيكل الإداري", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("المسميات المحلية (${activeOverrides.size})", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("القرى غير المرمزة", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("مطابقة GPS (PIP)", fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (selectedTab) {
                        0 -> HierarchyBrowserView(
                            governorates = governorates,
                            selectedGov = selectedGovernorate,
                            onSelectGov = { gov ->
                                selectedGovernorate = gov
                                selectedDistrict = null
                                selectedUzlah = null
                                villages = emptyList()
                                coroutineScope.launch {
                                    districts = cascadingSelector.getDistrictsForGovernorate(gov.admin1Pcode)
                                }
                            },
                            districts = districts,
                            selectedDist = selectedDistrict,
                            onSelectDist = { dist ->
                                selectedDistrict = dist
                                selectedUzlah = null
                                villages = emptyList()
                                coroutineScope.launch {
                                    uzlahs = cascadingSelector.getUzlahsForDistrict(dist.admin2Pcode)
                                }
                            },
                            uzlahs = uzlahs,
                            selectedUzlah = selectedUzlah,
                            onSelectUzlah = { uzlah ->
                                selectedUzlah = uzlah
                                coroutineScope.launch {
                                    villages = cascadingSelector.getVillagesForUzlah(uzlah.admin3Pcode)
                                }
                            },
                            villages = villages,
                            onProposeOverride = { showAddOverrideDialog = true }
                        )

                        1 -> OverridesListView(
                            overrides = activeOverrides,
                            onAddOverride = { showAddOverrideDialog = true },
                            onDeactivate = { overrideId ->
                                coroutineScope.launch {
                                    overrideManager.deactivateOverride(overrideId)
                                    activeOverrides = overrideManager.getAllActiveOverrides()
                                }
                            }
                        )

                        2 -> UnmappedVillagesView(
                            unmappedList = unmappedVillages
                        )

                        3 -> GpsPipTesterView(
                            lat = testLat,
                            lon = testLon,
                            onLatChange = { testLat = it },
                            onLonChange = { testLon = it },
                            result = gpsResolutionResult,
                            onResolve = {
                                val latD = testLat.toDoubleOrNull() ?: 16.9412
                                val lonD = testLon.toDoubleOrNull() ?: 43.7611
                                coroutineScope.launch {
                                    gpsResolutionResult = gpsResolver.resolveLocation(latD, lonD)
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
private fun KpiBadge(title: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HierarchyBrowserView(
    governorates: List<Admin1Governorate>,
    selectedGov: Admin1Governorate?,
    onSelectGov: (Admin1Governorate) -> Unit,
    districts: List<Admin2District>,
    selectedDist: Admin2District?,
    onSelectDist: (Admin2District) -> Unit,
    uzlahs: List<Admin3Uzlah>,
    selectedUzlah: Admin3Uzlah?,
    onSelectUzlah: (Admin3Uzlah) -> Unit,
    villages: List<AdminVillage>,
    onProposeOverride: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Step 1: Governorate Selection Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "1. المحافظة (Admin1 - رمز OCHA الرسمي)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        governorates.take(6).forEach { gov ->
                            val isSelected = selectedGov?.admin1Pcode == gov.admin1Pcode
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectGov(gov) },
                                label = { Text("${gov.nameAr} (${gov.admin1Pcode})", fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Step 2: District Selection
        if (selectedGov != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "2. المديريات في ${selectedGov.nameAr} (${districts.size} مديرية)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            districts.forEach { dist ->
                                val isSelected = selectedDist?.admin2Pcode == dist.admin2Pcode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectDist(dist) },
                                    label = { Text("${dist.nameAr} (${dist.admin2Pcode})", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step 3: Uzlah Selection
        if (selectedDist != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "3. العزل في مديرية ${selectedDist.nameAr} (${uzlahs.size} عزلة)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uzlahs.forEach { uzlah ->
                                val isSelected = selectedUzlah?.admin3Pcode == uzlah.admin3Pcode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectUzlah(uzlah) },
                                    label = { Text("${uzlah.nameAr} (${uzlah.admin3Pcode})", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step 4: Villages List
        if (selectedUzlah != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "4. القرى والمحلات التابعة لعزلة ${selectedUzlah.nameAr} (${villages.size} قرية)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            items(villages) { village ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                text = village.nameAr,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "المعرف: ${village.villageId} | المصدر: ${village.source.titleAr}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (village.latitude != null && village.longitude != null) {
                                Text(
                                    text = "إحداثيات: ${village.latitude}, ${village.longitude}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            color = if (village.mappingStatus == MappingStatus.MAPPED_CONFIRMED) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = village.mappingStatus.titleAr,
                                color = if (village.mappingStatus == MappingStatus.MAPPED_CONFIRMED) Color(0xFF2E7D32) else Color(0xFFE65100),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverridesListView(
    overrides: List<AdministrativeOverride>,
    onAddOverride: () -> Unit,
    onDeactivate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "نظام التعديلات والمسميات المحلية المعتمدة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "يتيح هذا النظام تسجيل المسميات الشائعة محلياً دون المساس بالمرجعية الرسمية (OCHA). يتم عرض المسمى المحلي بجانب الرسمي بشكل دائم.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (overrides.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد تعديلات محلية نشطة حالياً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(overrides) { override ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "المسمى المحلي: ${override.localNameAr}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = Color(0xFFE3F2FD),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = override.changeType.titleAr,
                                    fontSize = 10.sp,
                                    color = Color(0xFF1565C0),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المسمى الرسمي المرجعي: ${override.officialNameAr} (${override.officialPcodeOrId})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "السبب: ${override.reason}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "بواسطة: ${override.createdBy} | التاريخ: ${override.createdAt}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onDeactivate(override.overrideId) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلغاء التعديل والعودة للرسمي", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnmappedVillagesView(
    unmappedList: List<AdminVillage>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "القرى والمحلات غير المطابقة لرمز رسمي (UNMAPPED / NEEDS_REVIEW)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "وفقاً للسياسة الصارمة: يُمنع اختراع رموز P-Code للقرى غير المطابقة لمرجعية OCHA. تظل هذه السجلات مسجلة بصفة 'قيد المراجعة'.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(unmappedList) { village ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
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
                            text = village.nameAr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "المعرف: ${village.villageId} | الرمز: غير متوفر (UNMAPPED)",
                            fontSize = 11.sp,
                            color = Color(0xFFC62828)
                        )
                        Text(
                            text = "المصدر: ${village.source.titleAr}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = village.mappingStatus.titleAr,
                            color = Color(0xFFC62828),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GpsPipTesterView(
    lat: String,
    lon: String,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    result: GpsAdminResolutionResult?,
    onResolve: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "فحص مطابقة الإحداثيات الجغرافية (Point-in-Polygon Engine)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "يقوم المحرك بفحص نقطة الإحداثيات مقابل مضلعات الحدود الإدارية الرسمية المعتمدة (TopoJSON) وتحديد المحافظة والمديرية والعزلة وأقرب قرية بدون إنترنت.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = lat,
                            onValueChange = onLatChange,
                            label = { Text("خط العرض (Lat)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lon,
                            onValueChange = onLonChange,
                            label = { Text("خط الطول (Lon)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onResolve,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مطابقة الإحداثيات إدارياً")
                    }
                }
            }
        }

        if (result != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.isExactMatch) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "نتيجة المطابقة المكانية",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (result.isExactMatch) Color(0xFF1B5E20) else Color(0xFFE65100)
                            )
                            Surface(
                                color = if (result.isExactMatch) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (result.isExactMatch) "مطابقة كاملة" else "مطابقة جزئية",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "المحافظة: ${result.admin1NameAr ?: "غير محددة"} (${result.admin1Pcode ?: "N/A"})", fontSize = 13.sp)
                        Text(text = "المديرية: ${result.admin2NameAr ?: "غير محددة"} (${result.admin2Pcode ?: "N/A"})", fontSize = 13.sp)
                        Text(text = "العزلة: ${result.admin3NameAr ?: "غير محددة"} (${result.admin3Pcode ?: "N/A"})", fontSize = 13.sp)
                        if (result.nearestVillageNameAr != null) {
                            Text(
                                text = "أقرب قرية: ${result.nearestVillageNameAr} (المسافة: %.0f متر)".format(result.nearestVillageDistanceMeters ?: 0.0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = result.validationMessageAr,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
