package com.yemen.watersurvey.presentation.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.presentation.theme.*

@Composable
fun TextQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.Text?,
    onValueChanged: (RuntimeValue.Text?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val text = currentValue?.value ?: ""
    val isMultiline = question.appearance?.contains("multiline", ignoreCase = true) == true

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (!isReadOnly) {
                onValueChanged(if (newText.isNotBlank()) RuntimeValue.Text(newText) else null)
            }
        },
        placeholder = {
            if (!question.hintAr.isNullOrBlank()) {
                Text(question.hintAr, color = Slate400, fontSize = 13.sp)
            }
        },
        modifier = modifier.fillMaxWidth(),
        enabled = !isReadOnly,
        minLines = if (isMultiline) 3 else 1,
        maxLines = if (isMultiline) 6 else 1,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate100,
            unfocusedTextColor = Slate200,
            disabledTextColor = Slate400,
            disabledBorderColor = Slate800
        )
    )
}

@Composable
fun IntegerQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.Integer?,
    onValueChanged: (RuntimeValue.Integer?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    var rawText by remember(currentValue) {
        mutableStateOf(currentValue?.value?.toString() ?: "")
    }

    OutlinedTextField(
        value = rawText,
        onValueChange = { newText ->
            if (!isReadOnly) {
                rawText = newText
                val parsed = newText.trim().toLongOrNull()
                onValueChanged(if (parsed != null) RuntimeValue.Integer(parsed) else null)
            }
        },
        placeholder = {
            if (!question.hintAr.isNullOrBlank()) {
                Text(question.hintAr, color = Slate400, fontSize = 13.sp)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        enabled = !isReadOnly,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate100,
            unfocusedTextColor = Slate200,
            disabledTextColor = Slate400,
            disabledBorderColor = Slate800
        )
    )
}

@Composable
fun DecimalQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.Decimal?,
    onValueChanged: (RuntimeValue.Decimal?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    var rawText by remember(currentValue) {
        mutableStateOf(currentValue?.value?.toString() ?: "")
    }

    OutlinedTextField(
        value = rawText,
        onValueChange = { newText ->
            if (!isReadOnly) {
                rawText = newText
                val parsed = newText.trim().toDoubleOrNull()
                onValueChanged(if (parsed != null) RuntimeValue.Decimal(parsed) else null)
            }
        },
        placeholder = {
            if (!question.hintAr.isNullOrBlank()) {
                Text(question.hintAr, color = Slate400, fontSize = 13.sp)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        enabled = !isReadOnly,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate100,
            unfocusedTextColor = Slate200,
            disabledTextColor = Slate400,
            disabledBorderColor = Slate800
        )
    )
}

@Composable
fun DateQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.Date?,
    onValueChanged: (RuntimeValue.Date?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateStr = currentValue?.isoDate ?: ""

    OutlinedTextField(
        value = dateStr,
        onValueChange = { newDate ->
            if (!isReadOnly) {
                onValueChanged(if (newDate.isNotBlank()) RuntimeValue.Date(newDate.trim()) else null)
            }
        },
        placeholder = { Text("YYYY-MM-DD", color = Slate400, fontSize = 13.sp) },
        modifier = modifier.fillMaxWidth(),
        enabled = !isReadOnly,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate100,
            unfocusedTextColor = Slate200,
            disabledTextColor = Slate400,
            disabledBorderColor = Slate800
        )
    )
}

@Composable
fun SelectOneQuestionInput(
    question: QuestionElement,
    choiceList: ChoiceList?,
    currentValue: RuntimeValue.Choice?,
    onValueChanged: (RuntimeValue.Choice?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val items = choiceList?.items ?: emptyList()
    val selectedName = currentValue?.selectedName

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (items.isEmpty()) {
            Text("لا توجد خيارات متاحة", color = Slate400, fontSize = 12.sp)
        }

        items.forEach { item ->
            val isSelected = (item.name == selectedName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Slate800 else Slate900)
                    .border(
                        1.dp,
                        if (isSelected) Emerald500 else Slate800,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !isReadOnly) {
                        onValueChanged(if (isSelected) null else RuntimeValue.Choice(item.name))
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = {
                        if (!isReadOnly) {
                            onValueChanged(if (isSelected) null else RuntimeValue.Choice(item.name))
                        }
                    },
                    enabled = !isReadOnly,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Emerald500,
                        unselectedColor = Slate500
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.labelAr,
                        color = if (isSelected) Slate100 else Slate300,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (!item.labelEn.isNullOrBlank()) {
                        Text(
                            text = item.labelEn,
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectMultipleQuestionInput(
    question: QuestionElement,
    choiceList: ChoiceList?,
    currentValue: RuntimeValue.MultipleChoice?,
    onValueChanged: (RuntimeValue.MultipleChoice?) -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val items = choiceList?.items ?: emptyList()
    val selectedNames = currentValue?.selectedNames ?: emptySet()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (items.isEmpty()) {
            Text("لا توجد خيارات متاحة", color = Slate400, fontSize = 12.sp)
        }

        items.forEach { item ->
            val isSelected = selectedNames.contains(item.name)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Slate800 else Slate900)
                    .border(
                        1.dp,
                        if (isSelected) Cyan500 else Slate800,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !isReadOnly) {
                        val newSet = if (isSelected) {
                            selectedNames - item.name
                        } else {
                            selectedNames + item.name
                        }
                        onValueChanged(if (newSet.isNotEmpty()) RuntimeValue.MultipleChoice(newSet) else null)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        if (!isReadOnly) {
                            val newSet = if (checked) {
                                selectedNames + item.name
                            } else {
                                selectedNames - item.name
                            }
                            onValueChanged(if (newSet.isNotEmpty()) RuntimeValue.MultipleChoice(newSet) else null)
                        }
                    },
                    enabled = !isReadOnly,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Cyan500,
                        uncheckedColor = Slate500
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.labelAr,
                        color = if (isSelected) Slate100 else Slate300,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (!item.labelEn.isNullOrBlank()) {
                        Text(
                            text = item.labelEn,
                            color = Slate500,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSelectQuestionInput(
    question: QuestionElement,
    adminBinding: AdminTierBinding?,
    currentValue: RuntimeValue.AdminSelection?,
    onValueChanged: (RuntimeValue.AdminSelection?) -> Unit,
    adminLookupProvider: AdminLookupProvider?,
    parentPcodeValue: String?,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tier = adminBinding?.tier ?: AdminTier.GOVERNORATE
    var options by remember { mutableStateOf<List<AdminOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(parentPcodeValue, tier, adminLookupProvider) {
        if (adminLookupProvider != null) {
            isLoading = true
            options = try {
                when (tier) {
                    AdminTier.GOVERNORATE -> adminLookupProvider.getGovernorates()
                    AdminTier.DISTRICT -> if (!parentPcodeValue.isNullOrBlank()) adminLookupProvider.getDistricts(parentPcodeValue) else emptyList()
                    AdminTier.UZLAH -> if (!parentPcodeValue.isNullOrBlank()) adminLookupProvider.getUzlahs(parentPcodeValue) else emptyList()
                    AdminTier.VILLAGE -> if (!parentPcodeValue.isNullOrBlank()) adminLookupProvider.getVillages(parentPcodeValue) else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
            isLoading = false
        }
    }

    val selectedOption = options.find { it.pcode == currentValue?.pcode }
    val displayText = selectedOption?.let { "${it.nameAr} (${it.pcode})" } ?: currentValue?.pcode ?: ""

    ExposedDropdownMenuBox(
        expanded = isExpanded && !isReadOnly,
        onExpandedChange = { if (!isReadOnly) isExpanded = !isExpanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            placeholder = {
                Text(
                    text = if (isLoading) "جاري تحميل البيانات الإدارية..." else "اختر الموقع الإداري...",
                    color = Slate400,
                    fontSize = 13.sp
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = !isReadOnly,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Emerald500,
                unfocusedBorderColor = Slate700,
                focusedTextColor = Slate100,
                unfocusedTextColor = Slate200,
                disabledTextColor = Slate400,
                disabledBorderColor = Slate800
            )
        )

        ExposedDropdownMenu(
            expanded = isExpanded && !isReadOnly,
            onDismissRequest = { isExpanded = false },
            modifier = Modifier.background(Slate900)
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(if (isLoading) "جاري التحميل..." else "لا توجد بيانات متاحة", color = Slate400) },
                    onClick = { isExpanded = false }
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.nameAr, color = Slate100, fontSize = 14.sp)
                                Text(option.pcode, color = Slate400, fontSize = 11.sp)
                            }
                        },
                        onClick = {
                            onValueChanged(RuntimeValue.AdminSelection(pcode = option.pcode, tier = tier))
                            isExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GeopointQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.Geopoint?,
    onCaptureGps: () -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentValue != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "خط العرض: %.6f | خط الطول: %.6f".format(currentValue.latitude, currentValue.longitude),
                            color = Slate100,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "الارتفاع: %.1f م | الدقة: %.1f م".format(currentValue.altitudeM, currentValue.accuracyM),
                            color = if (currentValue.accuracyM < 15.0f) Emerald400 else Amber400,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "GPS مثبت",
                        tint = if (currentValue.accuracyM < 15.0f) Emerald400 else Amber400,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Text(
                    text = "لم يتم التقاط إحداثيات GPS بعد.",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onCaptureGps,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadOnly
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentValue != null) "إعادة التقاط إحداثيات GPS" else "التقاط إحداثيات GPS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun ImageQuestionInput(
    question: QuestionElement,
    currentValue: RuntimeValue.ImageRef?,
    onCaptureImage: () -> Unit,
    isReadOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentValue != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تم التقاط الصورة الميدانية بنجاح",
                            color = Slate100,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "المعرف: ${currentValue.attachmentId.take(12)}...",
                            color = Emerald400,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "تم التقاط الصورة",
                        tint = Emerald400,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Text(
                    text = "لم يتم التقاط صورة لهذا السؤال بعد.",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onCaptureImage,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadOnly
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Slate950, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (currentValue != null) "إعادة التقاط الصورة" else "التقاط صورة ميدانية",
                    color = Slate950,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
