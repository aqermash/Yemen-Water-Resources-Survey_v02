package com.yemen.watersurvey.presentation.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.sync.SupervisorSyncWorkspaceManager
import com.yemen.watersurvey.data.database.SurveyAppDatabase
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 9.3: Offline Supervisor Synchronization Workspace Screen.
 *
 * Provides central offline dashboard and multi-package inbox management:
 * 1. Tracks package lifecycles (RECEIVED, VALIDATED, REVIEW_PENDING, PARTIALLY_MERGED, MERGED, REJECTED, ARCHIVED).
 * 2. Visualizes real-time metrics, conflict counters, and local survey statistics.
 * 3. Enforces isolated package reviews and seamless routing to validation and controlled merge.
 * 4. Generates district-level aggregation summaries without internet connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupervisorSyncDashboardScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { SurveyAppDatabase.getInstance(context) }
    val workspaceManager = remember { SupervisorSyncWorkspaceManager(context, database) }

    var packages by remember { mutableStateOf<List<SyncPackageWorkspaceItem>>(emptyList()) }
    var dashboardStats by remember { mutableStateOf(WorkspaceDashboardStats()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilterTab by remember { mutableStateOf("ALL") }

    // Dialog States
    var showDistrictSummaryDialog by remember { mutableStateOf(false) }
    var districtSummaryData by remember { mutableStateOf<DistrictSyncSummary?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedPackageHistory by remember { mutableStateOf<List<SyncPackageHistoryRecord>>(emptyList()) }
    var historyPackageTitle by remember { mutableStateOf("") }

    // Load initial data
    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                workspaceManager.discoverAndRegisterPackages()
                val pkgList = workspaceManager.getWorkspacePackages(includeArchived = true)
                val stats = workspaceManager.computeDashboardStats()
                withContext(Dispatchers.Main) {
                    packages = pkgList
                    dashboardStats = stats
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Filter packages based on active tab
    val filteredPackages = remember(packages, selectedFilterTab) {
        when (selectedFilterTab) {
            "INBOX" -> packages.filter { it.state == SyncPackageState.RECEIVED && !it.isArchived }
            "PENDING" -> packages.filter { (it.state == SyncPackageState.VALIDATED || it.state == SyncPackageState.REVIEW_PENDING) && !it.isArchived }
            "MERGED" -> packages.filter { (it.state == SyncPackageState.MERGED || it.state == SyncPackageState.PARTIALLY_MERGED) && !it.isArchived }
            "REJECTED" -> packages.filter { it.state == SyncPackageState.REJECTED && !it.isArchived }
            "ARCHIVED" -> packages.filter { it.isArchived }
            else -> packages.filter { !it.isArchived }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "مساحة عمل مزامنة المشرف",
                                color = Slate100,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إدارة واستلام حزم التبادل الميدانية (.ywsync) والتحقق والمراجعة بدون إنترنت",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { onNavigate(ScreenRoute.Export) }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "رجوع", tint = Slate300)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    val summary = workspaceManager.generateDistrictSyncSummary(
                                        governorate = "صعدة",
                                        district = "سحار"
                                    )
                                    withContext(Dispatchers.Main) {
                                        districtSummaryData = summary
                                        showDistrictSummaryDialog = true
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Summarize, contentDescription = "تقرير المديرية", tint = Cyan400)
                        }
                        IconButton(onClick = { refreshData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Sky400)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
                )
            },
            containerColor = Slate950
        ) { paddingValues ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status banner if present
                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Sky950),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Sky500.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Sky400, modifier = Modifier.size(16.dp))
                            Text(text = msg, color = Sky200, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { statusMessage = null },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Slate400, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // 1. SUMMARY METRICS DASHBOARD
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Dashboard, contentDescription = null, tint = Sky400, modifier = Modifier.size(16.dp))
                                Text("مؤشرات مساحة عمل المشرف", color = Slate200, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "100% غير متصل (Offline)",
                                color = Emerald400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Metric counters row 1: Packages
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WorkspaceStatBadge(
                                label = "إجمالي الحزم",
                                count = dashboardStats.totalReceivedPackages,
                                color = Slate400,
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatBadge(
                                label = "بانتظار المراجعة",
                                count = dashboardStats.pendingReviewsCount,
                                color = Amber400,
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatBadge(
                                label = "حزم مدمجة",
                                count = dashboardStats.completedMergesCount,
                                color = Emerald400,
                                modifier = Modifier.weight(1f)
                            )
                            WorkspaceStatBadge(
                                label = "تعارضات عالقة",
                                count = dashboardStats.conflictsWaiting,
                                color = Rose400,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Local Survey Database Stats Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate950)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SurveyDbItemBadge("إجمالي الاستمارات", "${dashboardStats.databaseTotalSurveys}", Slate200)
                            SurveyDbItemBadge("آبار", "${dashboardStats.databaseWellsCount}", Sky400)
                            SurveyDbItemBadge("عيون", "${dashboardStats.databaseSpringsCount}", Emerald400)
                            SurveyDbItemBadge("سدود", "${dashboardStats.databaseDamsCount}", Amber400)
                            SurveyDbItemBadge("مرفقات", "${dashboardStats.databaseTotalAttachments}", Cyan400)
                        }
                    }
                }

                // 2. FILTER TABS
                ScrollableTabRow(
                    selectedTabIndex = listOf("ALL", "INBOX", "PENDING", "MERGED", "REJECTED", "ARCHIVED").indexOf(selectedFilterTab).coerceAtLeast(0),
                    containerColor = Slate900,
                    contentColor = Sky400,
                    edgePadding = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Slate800, RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedFilterTab == "ALL",
                        onClick = { selectedFilterTab = "ALL" },
                        text = { Text("الكل النشط (${packages.count { !it.isArchived }})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedFilterTab == "INBOX",
                        onClick = { selectedFilterTab = "INBOX" },
                        text = { Text("صندوق الوارد (${packages.count { it.state == SyncPackageState.RECEIVED && !it.isArchived }})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedFilterTab == "PENDING",
                        onClick = { selectedFilterTab = "PENDING" },
                        text = { Text("بانتظار المراجعة (${packages.count { (it.state == SyncPackageState.VALIDATED || it.state == SyncPackageState.REVIEW_PENDING) && !it.isArchived }})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedFilterTab == "MERGED",
                        onClick = { selectedFilterTab = "MERGED" },
                        text = { Text("مدمجة (${packages.count { (it.state == SyncPackageState.MERGED || it.state == SyncPackageState.PARTIALLY_MERGED) && !it.isArchived }})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedFilterTab == "REJECTED",
                        onClick = { selectedFilterTab = "REJECTED" },
                        text = { Text("مرفوضة (${packages.count { it.state == SyncPackageState.REJECTED && !it.isArchived }})", fontSize = 11.sp) }
                    )
                    Tab(
                        selected = selectedFilterTab == "ARCHIVED",
                        onClick = { selectedFilterTab = "ARCHIVED" },
                        text = { Text("الأرشيف (${packages.count { it.isArchived }})", fontSize = 11.sp) }
                    )
                }

                // 3. PACKAGES LIST
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Sky500)
                    }
                } else if (filteredPackages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900)
                            .border(1.dp, Slate800, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Slate600, modifier = Modifier.size(48.dp))
                            Text(
                                text = "لا توجد حزم مطابقة في هذا التصنيف",
                                color = Slate400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "يمكنك تصدير حزمة جديدة أو وضع ملفات .ywsync في مجلد التطبيق المخصص.",
                                color = Slate500,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { onNavigate(ScreenRoute.SurveySyncImport) },
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, tint = Slate950, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("استيراد حزمة من وحدة التخزين", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPackages, key = { it.packageId }) { item ->
                            WorkspacePackageCard(
                                item = item,
                                onValidate = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            val res = workspaceManager.validatePackageIntegrity(item.packageId)
                                            withContext(Dispatchers.Main) {
                                                if (res.isSuccess) {
                                                    statusMessage = "تم التحقق من سلامة الحزمة ${item.packageId} ومطابقة البصمة بنجاح."
                                                } else {
                                                    statusMessage = "خطأ في التحقق من الحزمة: ${res.exceptionOrNull()?.message}"
                                                }
                                                refreshData()
                                            }
                                        }
                                    }
                                },
                                onReviewMerge = {
                                    onNavigate(ScreenRoute.SurveyMergeReview)
                                },
                                onArchiveToggle = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            workspaceManager.setPackageArchived(item.packageId, !item.isArchived)
                                            withContext(Dispatchers.Main) {
                                                statusMessage = if (!item.isArchived) "تمت أرشفة الحزمة ${item.packageId}." else "تمت استعادة الحزمة من الأرشيف."
                                                refreshData()
                                            }
                                        }
                                    }
                                },
                                onShowHistory = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            val history = workspaceManager.getPackageHistory(item.packageId)
                                            withContext(Dispatchers.Main) {
                                                selectedPackageHistory = history
                                                historyPackageTitle = item.packageId
                                                showHistoryDialog = true
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- DISTRICT SUMMARY REPORT DIALOG ---
        if (showDistrictSummaryDialog && districtSummaryData != null) {
            val summary = districtSummaryData!!
            AlertDialog(
                onDismissRequest = { showDistrictSummaryDialog = false },
                containerColor = Slate900,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Summarize, contentDescription = null, tint = Cyan400)
                        Text(
                            text = "تقرير تجميع المزامنة للمديرية",
                            color = Slate100,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Location info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate950)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المحافظة: ${summary.governorate}", color = Slate300, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("المديرية: ${summary.district}", color = Cyan400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Counts breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryStatChip("إجمالي الاستمارات", "${summary.totalSurveys}", Sky400, Modifier.weight(1f))
                            SummaryStatChip("آبار", "${summary.wellsCount}", Sky300, Modifier.weight(1f))
                            SummaryStatChip("عيون", "${summary.springsCount}", Emerald400, Modifier.weight(1f))
                            SummaryStatChip("سدود", "${summary.damsCount}", Amber400, Modifier.weight(1f))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryStatChip("معتمدة (Approved)", "${summary.approvedCount}", Emerald400, Modifier.weight(1f))
                            SummaryStatChip("مكتملة (Completed)", "${summary.completedCount}", Cyan400, Modifier.weight(1f))
                            SummaryStatChip("مسودات (Draft)", "${summary.draftCount}", Slate400, Modifier.weight(1f))
                        }

                        Divider(color = Slate800)

                        // Enumerator sources section
                        Text("مساهمات الباحثين والأجهزة المصدرية:", color = Slate200, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        if (summary.enumeratorSources.isEmpty()) {
                            Text("لم يتم تسجيل مساهمات باحثين بعد.", color = Slate500, fontSize = 10.sp)
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate950)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                summary.enumeratorSources.forEach { enumItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(enumItem.enumeratorUsername, color = Slate200, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("جهاز: ${enumItem.sourceDeviceId}", color = Slate500, fontSize = 9.sp)
                                        }
                                        Text("${enumItem.totalSurveysSubmitted} استمارة (${enumItem.submittedPackagesCount} حزمة)", color = Sky400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Audit & Revisions metadata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("سجلات التدقيق: ${summary.totalAuditLogsCount}", color = Slate400, fontSize = 10.sp)
                            Text("المراجعات التاريخية: ${summary.totalRevisionsCount}", color = Slate400, fontSize = 10.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showDistrictSummaryDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Sky500)
                    ) {
                        Text("إغلاق التقرير", color = Slate950, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // --- PACKAGE HISTORY LOG DIALOG ---
        if (showHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                containerColor = Slate900,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, tint = Amber400)
                        Text(
                            text = "سجل العمليات للحزمة: $historyPackageTitle",
                            color = Slate100,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    if (selectedPackageHistory.isEmpty()) {
                        Text("لا يوجد سجل عمليات مسجل لهذه الحزمة بعد.", color = Slate400, fontSize = 11.sp)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedPackageHistory) { history ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Slate950),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(history.actionDescription, color = Slate200, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(history.timestamp, color = Slate500, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("المستخدم: ${history.actorId} (${history.actorRole})", color = Slate400, fontSize = 9.sp)
                                            Text("${history.fromState.titleAr} ➔ ${history.toState.titleAr}", color = Amber400, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHistoryDialog = false }) {
                        Text("إغلاق", color = Sky400, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

/**
 * Individual package item card in workspace dashboard.
 */
@Composable
fun WorkspacePackageCard(
    item: SyncPackageWorkspaceItem,
    onValidate: () -> Unit,
    onReviewMerge: () -> Unit,
    onArchiveToggle: () -> Unit,
    onShowHistory: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                when (item.state) {
                    SyncPackageState.MERGED -> Emerald500.copy(alpha = 0.4f)
                    SyncPackageState.REVIEW_PENDING -> Amber500.copy(alpha = 0.4f)
                    SyncPackageState.REJECTED -> Rose500.copy(alpha = 0.4f)
                    SyncPackageState.VALIDATED -> Sky500.copy(alpha = 0.4f)
                    else -> Slate800
                },
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Package ID & State Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.Archive,
                        contentDescription = null,
                        tint = when (item.state) {
                            SyncPackageState.MERGED -> Emerald400
                            SyncPackageState.REVIEW_PENDING -> Amber400
                            SyncPackageState.REJECTED -> Rose400
                            SyncPackageState.VALIDATED -> Sky400
                            else -> Slate400
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = item.packageId,
                        color = Slate100,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                PackageStateBadge(item.state)
            }

            // Sub-header: Origin & Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الباحث: ${item.senderUsername} (${item.senderRole})",
                    color = Slate300,
                    fontSize = 10.sp
                )
                Text(
                    text = "${item.governorate} / ${item.district}",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }

            // Stats row: Surveys, Attachments, Size, Conflicts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate950)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${item.surveyCount} استمارة", color = Sky300, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("${item.attachmentCount} مرفق", color = Slate300, fontSize = 10.sp)
                Text("${item.packageSizeBytes / 1024} KB", color = Slate400, fontSize = 10.sp)

                if (item.conflictsCount > 0) {
                    Text("تعارضات: ${item.conflictsCount}", color = Rose400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else if (item.newRecordsCount > 0) {
                    Text("جديدة: ${item.newRecordsCount}", color = Emerald400, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("جاهزة", color = Slate400, fontSize = 10.sp)
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.state == SyncPackageState.RECEIVED) {
                    Button(
                        onClick = onValidate,
                        colors = ButtonDefaults.buttonColors(containerColor = Sky500),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص الحزمة", color = Slate950, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onReviewMerge,
                        colors = ButtonDefaults.buttonColors(containerColor = if (item.state == SyncPackageState.MERGED) Emerald600 else Amber500),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MergeType, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (item.state == SyncPackageState.MERGED) "عرض الدمج المكتمل" else "مراجعة ودمج", color = Slate950, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onArchiveToggle,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (item.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(if (item.isArchived) "استعادة" else "أرشفة", fontSize = 10.sp)
                }

                IconButton(
                    onClick = onShowHistory,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = "السجل", tint = Slate400, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun PackageStateBadge(state: SyncPackageState) {
    val (bg, text, color) = when (state) {
        SyncPackageState.RECEIVED -> Triple(Cyan950, "مستلمة", Cyan400)
        SyncPackageState.VALIDATED -> Triple(Sky950, "تم الفحص", Sky400)
        SyncPackageState.REVIEW_PENDING -> Triple(Amber950, "بانتظار المراجعة", Amber400)
        SyncPackageState.PARTIALLY_MERGED -> Triple(Amber950, "مدمجة جزئياً", Amber300)
        SyncPackageState.MERGED -> Triple(Emerald950, "مدمجة بالكامل", Emerald400)
        SyncPackageState.REJECTED -> Triple(Rose950, "مرفوضة", Rose400)
        SyncPackageState.ARCHIVED -> Triple(Slate950, "مؤرشفة", Slate400)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun WorkspaceStatBadge(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Slate950)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "$count", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = Slate400, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun SurveyDbItemBadge(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = Slate400, fontSize = 10.sp)
        Text(text = value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SummaryStatChip(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Slate950)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Slate400, fontSize = 8.sp, textAlign = TextAlign.Center)
    }
}
