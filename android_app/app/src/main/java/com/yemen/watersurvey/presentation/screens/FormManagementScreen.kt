package com.yemen.watersurvey.presentation.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.core.form.FormPackageManager
import com.yemen.watersurvey.domain.model.FormPackage
import com.yemen.watersurvey.domain.model.PackageImportResult
import com.yemen.watersurvey.domain.model.PackageValidationResult
import com.yemen.watersurvey.presentation.navigation.ScreenRoute
import com.yemen.watersurvey.presentation.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Screen for managing versioned survey form packages in the Yemen Water Survey System.
 * Supports offline importing, package structure validation, version activation, and inspection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormManagementScreen(
    onNavigate: (ScreenRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val packageManager = remember { FormPackageManager(context) }

    var packagesList by remember { mutableStateOf<List<FormPackage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    var showImportDialog by remember { mutableStateOf(false) }
    var selectedPackageForDetails by remember { mutableStateOf<FormPackage?>(null) }
    var selectedPackageValidation by remember { mutableStateOf<PackageValidationResult?>(null) }

    fun refreshPackages() {
        coroutineScope.launch {
            isLoading = true
            try {
                packagesList = packageManager.getInstalledPackages()
            } catch (e: Exception) {
                statusMessage = "خطأ في تحميل الحزم: ${e.message}"
                isErrorMessage = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshPackages()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "إدارة حزم الاستمارات الميدانية (Form Packages)",
                    color = Slate100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "استيراد وإدارة إصدارات استمارات المسح وقوالب الـ PDF بشكل مستقل وبدون إنترنت",
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = { showImportDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Slate950, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استيراد حزمة", color = Slate950, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = Slate800)

        // Status Alert
        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isErrorMessage) Rose950 else Emerald950),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (isErrorMessage) Rose500 else Emerald500, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isErrorMessage) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isErrorMessage) Rose400 else Emerald400
                    )
                    Text(
                        text = msg,
                        color = if (isErrorMessage) Rose300 else Emerald300,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Packages Overview Summary Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "إجمالي الحزم المثبتة", color = Slate400, fontSize = 10.sp)
                    Text(text = "${packagesList.size}", color = Slate100, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "الاستمارات النشطة للعمل", color = Slate400, fontSize = 10.sp)
                    Text(
                        text = "${packagesList.count { it.isActive }}",
                        color = Emerald400,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // List of Installed Packages
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald500)
            }
        } else if (packagesList.isEmpty()) {
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
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = Slate600, modifier = Modifier.size(48.dp))
                    Text(
                        text = "لا توجد حزم استمارات مثبتة حالياً في النظام",
                        color = Slate300,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "قم باستيراد حزمة بصيغة .zip أو تثبيت الحزم الافتراضية للآبار والعيون والسدود للبدء في جمع البيانات الميدانية.",
                        color = Slate500,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                val sampleZip = File(context.cacheDir, "sample_well_v1.zip")
                                packageManager.createSamplePackageZip(sampleZip, "form-well-standard", "1.0", "استمارة حصر وتوثيق آبار المياه القياسية", "الاستمارة المعتمدة رسمياً لتوثيق الآبار الارتوازية واليدوية", "WELL")
                                val res = packageManager.importPackageFromZip(sampleZip)
                                when (res) {
                                    is PackageImportResult.Success -> {
                                        statusMessage = "تم تثبيت وتفعيل الحزمة الافتراضية بنجاح"
                                        isErrorMessage = false
                                        refreshPackages()
                                    }
                                    is PackageImportResult.Failure -> {
                                        statusMessage = "فشل التثبيت: ${res.errors.joinToString()}"
                                        isErrorMessage = true
                                    }
                                }
                                isLoading = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تثبيت الحزمة القياسية المعتمدة (Well v1.0)", color = Emerald400, fontSize = 11.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(packagesList, key = { it.compositeId }) { pkg ->
                    PackageCard(
                        formPackage = pkg,
                        onActivate = {
                            coroutineScope.launch {
                                val ok = packageManager.activateVersion(pkg.formId, pkg.version)
                                if (ok) {
                                    statusMessage = "تم تفعيل الإصدار ${pkg.version} للاستمارة ${pkg.name}"
                                    isErrorMessage = false
                                    refreshPackages()
                                } else {
                                    statusMessage = "فشل في تفعيل الإصدار"
                                    isErrorMessage = true
                                }
                            }
                        },
                        onViewDetails = {
                            selectedPackageForDetails = pkg
                            val pkgDir = File(pkg.packagePath)
                            selectedPackageValidation = packageManager.validatePackage(pkgDir)
                        },
                        onDelete = {
                            coroutineScope.launch {
                                val ok = packageManager.deletePackage(pkg.formId, pkg.version)
                                if (ok) {
                                    statusMessage = "تم حذف الحزمة بنجاح"
                                    isErrorMessage = false
                                    refreshPackages()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Import Dialog
    if (showImportDialog) {
        ImportPackageDialog(
            onDismiss = { showImportDialog = false },
            onImportSample = { formId, version, nameAr, surveyType ->
                showImportDialog = false
                coroutineScope.launch {
                    isLoading = true
                    val zipFile = File(context.cacheDir, "pkg_${formId}_${version}.zip")
                    packageManager.createSamplePackageZip(
                        outputZipFile = zipFile,
                        formId = formId,
                        version = version,
                        nameAr = nameAr,
                        descriptionAr = "حزمة استمارة ميدانية معتمدة لقطاع الموارد المائية",
                        surveyType = surveyType
                    )
                    val res = packageManager.importPackageFromZip(zipFile)
                    when (res) {
                        is PackageImportResult.Success -> {
                            statusMessage = res.message
                            isErrorMessage = false
                            refreshPackages()
                        }
                        is PackageImportResult.Failure -> {
                            statusMessage = "خطأ في الاستيراد (${res.stage}): ${res.errors.joinToString(", ")}"
                            isErrorMessage = true
                        }
                    }
                    isLoading = false
                }
            }
        )
    }

    // Package Details Dialog
    selectedPackageForDetails?.let { pkg ->
        PackageDetailsDialog(
            formPackage = pkg,
            validationResult = selectedPackageValidation,
            onDismiss = {
                selectedPackageForDetails = null
                selectedPackageValidation = null
            }
        )
    }
}

@Composable
fun PackageCard(
    formPackage: FormPackage,
    onActivate: () -> Unit,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (formPackage.isActive) Emerald500.copy(alpha = 0.5f) else Slate800,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Title, Version, Active status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (formPackage.isActive) Emerald950 else Slate800)
                            .border(1.dp, if (formPackage.isActive) Emerald500 else Slate700, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (formPackage.isActive) "نشط ومفعل" else "إصدار أرشيفي",
                            color = if (formPackage.isActive) Emerald300 else Slate400,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Sky950)
                            .border(1.dp, Sky500.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "الإصدار ${formPackage.version}",
                            color = Sky300,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = formPackage.formId,
                    color = Slate500,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Form Name and Description
            Text(
                text = formPackage.name,
                color = Slate100,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            if (formPackage.description.isNotBlank()) {
                Text(
                    text = formPackage.description,
                    color = Slate400,
                    fontSize = 10.5.sp,
                    maxLines = 2
                )
            }

            // Publisher and Installation Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الناشر: ${formPackage.publisher}",
                    color = Slate400,
                    fontSize = 9.5.sp
                )
                Text(
                    text = "تاريخ التثبيت: ${formPackage.installationDate}",
                    color = Slate500,
                    fontSize = 9.5.sp
                )
            }

            // Component files indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FileIndicatorPill("definition.json", formPackage.hasFormDefinition)
                FileIndicatorPill("choices.json", formPackage.hasChoices)
                FileIndicatorPill("template.pdf", formPackage.hasPdfTemplate)
                FileIndicatorPill("mapping.json", formPackage.hasPdfMapping)
            }

            Divider(color = Slate800)

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onViewDetails,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فحص الحزمة", fontSize = 10.5.sp)
                    }

                    if (!formPackage.isActive) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Rose400, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (!formPackage.isActive) {
                    Button(
                        onClick = onActivate,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Slate950, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تفعيل هذا الإصدار", color = Slate950, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                        Text("الإصدار المعتمد حالياً", color = Emerald400, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FileIndicatorPill(label: String, isPresent: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isPresent) Slate800 else Slate900)
            .border(1.dp, if (isPresent) Slate700 else Slate800, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = if (isPresent) Slate300 else Slate600,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ImportPackageDialog(
    onDismiss: () -> Unit,
    onImportSample: (formId: String, version: String, nameAr: String, surveyType: String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "استيراد حزمة استمارة ميدانية (Package Import)",
                color = Slate100,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "اختر حزمة من النماذج الرسمية المعتمدة لقطاع المياه والبيئة لتثبيتها محلياً:",
                    color = Slate400,
                    fontSize = 11.sp
                )

                // Package 1: Well Standard v1.0
                PackageSelectionOption(
                    title = "استمارة الآبار الميدانية القياسية (v1.0)",
                    subtitle = "النموذج الأساسي لتوثيق الآبار والضخ ومواصفات الحفر",
                    badge = "WELL v1.0",
                    onClick = {
                        onImportSample("form-well-standard", "1.0", "استمارة حصر وتوثيق آبار المياه القياسية", "WELL")
                    }
                )

                // Package 2: Well Advanced v2.0
                PackageSelectionOption(
                    title = "استمارة الآبار الميدانية المحدثة (v2.0)",
                    subtitle = "إصدار حديث يدعم مواصفات الطاقة الشمسية وتصريف الضخ",
                    badge = "WELL v2.0",
                    onClick = {
                        onImportSample("form-well-standard", "2.0", "استمارة حصر وتوثيق آبار المياه المحدثة", "WELL")
                    }
                )

                // Package 3: Spring Survey v1.0
                PackageSelectionOption(
                    title = "استمارة العيون والينابيع الطبيعية (v1.0)",
                    subtitle = "توثيق تدفق العيون والخصائص الهيدرولوجية والموسمية",
                    badge = "SPRING v1.0",
                    onClick = {
                        onImportSample("form-spring-standard", "1.0", "استمارة حصر وتوثيق العيون والينابيع", "SPRING")
                    }
                )

                // Package 4: Dam & Barrier Survey v1.0
                PackageSelectionOption(
                    title = "استمارة السدود والحواجز المائية (v1.0)",
                    subtitle = "توثيق السعة التخزينية والحالة الإنشائية للسدود والمصدات",
                    badge = "DAM v1.0",
                    onClick = {
                        onImportSample("form-dam-standard", "1.0", "استمارة حصر وتوثيق السدود والحواجز", "DAM")
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = Slate400)
            }
        }
    )
}

@Composable
fun PackageSelectionOption(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate950),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Slate100, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Slate500, fontSize = 9.5.sp)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Sky950)
                    .border(1.dp, Sky500.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = badge, color = Sky300, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PackageDetailsDialog(
    formPackage: FormPackage,
    validationResult: PackageValidationResult?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Emerald400)
                Text(
                    text = "تفاصيل وفحص الحزمة: ${formPackage.name}",
                    color = Slate100,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Form ID and Version
                DetailRow("معرّف الاستمارة (Form ID):", formPackage.formId)
                DetailRow("رقم الإصدار (Version):", formPackage.version)
                DetailRow("الجهة الناشرة:", formPackage.publisher)
                DetailRow("تاريخ التثبيت:", formPackage.installationDate)
                DetailRow("مسار الحزمة المحلي:", formPackage.packagePath)

                // SHA-256 Checksum
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "بصمة التحقق المشفرة (SHA-256 Checksum):", color = Slate400, fontSize = 9.5.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Slate950)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = formPackage.checksum.ifBlank { validationResult?.calculatedChecksum ?: "-" },
                            color = Emerald400,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Validation Status
                validationResult?.let { res ->
                    Divider(color = Slate800)
                    Text(
                        text = if (res.isValid) "حالة التحقق الهيكلي: متوافقة 100% مع معايير الحزم الرسمية" else "يوجد أخطاء في فحص الحزمة",
                        color = if (res.isValid) Emerald400 else Rose400,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (res.errors.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            res.errors.forEach { err ->
                                Text(text = "• $err", color = Rose300, fontSize = 9.5.sp)
                            }
                        }
                    }

                    if (res.warnings.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            res.warnings.forEach { warn ->
                                Text(text = "⚠ $warn", color = Amber400, fontSize = 9.5.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800)
            ) {
                Text("إغلاق", color = Slate100)
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Slate400, fontSize = 10.sp)
        Text(
            text = value,
            color = Slate200,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
