package com.yemen.watersurvey.presentation.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yemen.watersurvey.domain.model.*
import com.yemen.watersurvey.core.expression.DefaultFormExpressionEvaluator
import com.yemen.watersurvey.core.expression.FormExpressionEvaluator
import com.yemen.watersurvey.presentation.theme.*

/**
 * Dynamic Jetpack Compose Form Renderer.
 *
 * Implements Phase 12B specification under C6.1.2:
 * - Recursively renders hierarchical FormDefinition (GroupElement.children is authoritative)
 * - Renders all supported QuestionDataTypes dynamically
 * - Decoupled from administrative datasets via AdminLookupProvider
 * - Decoupled from expression execution via FormExpressionEvaluator interface boundary
 * - Completely generic: zero hard-coded form IDs or question names
 */
@Composable
fun DynamicFormRenderer(
    formDefinition: FormDefinition,
    choiceLists: Map<String, ChoiceList>,
    formState: DynamicFormState,
    onValueChanged: (fieldName: String, value: RuntimeValue?) -> Unit,
    modifier: Modifier = Modifier,
    expressionEvaluator: FormExpressionEvaluator = DefaultFormExpressionEvaluator,
    adminLookupProvider: AdminLookupProvider? = null,
    onCaptureGps: ((fieldName: String) -> Unit)? = null,
    onCaptureImage: ((fieldName: String) -> Unit)? = null,
    isReadOnly: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Recursive rendering of root elements
        FormElementTreeRenderer(
            elements = formDefinition.rootElements,
            choiceLists = choiceLists,
            formState = formState,
            onValueChanged = onValueChanged,
            expressionEvaluator = expressionEvaluator,
            adminLookupProvider = adminLookupProvider,
            onCaptureGps = onCaptureGps,
            onCaptureImage = onCaptureImage,
            isReadOnly = isReadOnly,
            depth = 0
        )
    }
}

/**
 * Recursively renders a list of FormElements, preserving source order and tree hierarchy.
 */
@Composable
fun FormElementTreeRenderer(
    elements: List<FormElement>,
    choiceLists: Map<String, ChoiceList>,
    formState: DynamicFormState,
    onValueChanged: (fieldName: String, value: RuntimeValue?) -> Unit,
    expressionEvaluator: FormExpressionEvaluator,
    adminLookupProvider: AdminLookupProvider?,
    onCaptureGps: ((fieldName: String) -> Unit)?,
    onCaptureImage: ((fieldName: String) -> Unit)?,
    isReadOnly: Boolean,
    depth: Int
) {
    elements.forEach { element ->
        val isRelevant = expressionEvaluator.isElementRelevant(element, formState.toExpressionContext())
        AnimatedVisibility(
            visible = isRelevant,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            when (element) {
                is GroupElement -> {
                    GroupElementRenderer(
                        group = element,
                        choiceLists = choiceLists,
                        formState = formState,
                        onValueChanged = onValueChanged,
                        expressionEvaluator = expressionEvaluator,
                        adminLookupProvider = adminLookupProvider,
                        onCaptureGps = onCaptureGps,
                        onCaptureImage = onCaptureImage,
                        isReadOnly = isReadOnly,
                        depth = depth
                    )
                }
                is QuestionElement -> {
                    QuestionElementRenderer(
                        question = element,
                        choiceLists = choiceLists,
                        formState = formState,
                        onValueChanged = onValueChanged,
                        expressionEvaluator = expressionEvaluator,
                        adminLookupProvider = adminLookupProvider,
                        onCaptureGps = onCaptureGps,
                        onCaptureImage = onCaptureImage,
                        isReadOnly = isReadOnly
                    )
                }
                is CalculateElement -> {
                    // Calculations are in-memory non-visual evaluations
                }
                is HiddenElement -> {
                    // Hidden fields are non-visual system parameters
                }
                is SystemTimestampElement -> {
                    // Non-visual session audit capture
                }
            }
        }
    }
}

/**
 * Renders a visual group container (section card) and recursively renders its children.
 */
@Composable
fun GroupElementRenderer(
    group: GroupElement,
    choiceLists: Map<String, ChoiceList>,
    formState: DynamicFormState,
    onValueChanged: (fieldName: String, value: RuntimeValue?) -> Unit,
    expressionEvaluator: FormExpressionEvaluator,
    adminLookupProvider: AdminLookupProvider?,
    onCaptureGps: ((fieldName: String) -> Unit)?,
    onCaptureImage: ((fieldName: String) -> Unit)?,
    isReadOnly: Boolean,
    depth: Int
) {
    val containerBg = if (depth == 0) Slate900 else Slate850
    val borderColor = if (depth == 0) Slate800 else Slate700

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(borderColor))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Group Header
            Column {
                Text(
                    text = group.labelAr.ifBlank { group.name },
                    color = Slate100,
                    fontSize = if (depth == 0) 16.sp else 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!group.labelEn.isNullOrBlank()) {
                    Text(
                        text = group.labelEn,
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = borderColor, thickness = 1.dp)

            // Recursive Children
            FormElementTreeRenderer(
                elements = group.children,
                choiceLists = choiceLists,
                formState = formState,
                onValueChanged = onValueChanged,
                expressionEvaluator = expressionEvaluator,
                adminLookupProvider = adminLookupProvider,
                onCaptureGps = onCaptureGps,
                onCaptureImage = onCaptureImage,
                isReadOnly = isReadOnly,
                depth = depth + 1
            )
        }
    }
}

/**
 * Renders an interactive user-input QuestionElement according to its QuestionDataType.
 */
@Composable
fun QuestionElementRenderer(
    question: QuestionElement,
    choiceLists: Map<String, ChoiceList>,
    formState: DynamicFormState,
    onValueChanged: (fieldName: String, value: RuntimeValue?) -> Unit,
    expressionEvaluator: FormExpressionEvaluator,
    adminLookupProvider: AdminLookupProvider?,
    onCaptureGps: ((fieldName: String) -> Unit)?,
    onCaptureImage: ((fieldName: String) -> Unit)?,
    isReadOnly: Boolean
) {
    val error = formState.validationErrors[question.name]
    val currentValue = formState.getValue(question.name)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Question Label with Required Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = question.labelAr.ifBlank { question.name },
                color = Slate200,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (question.isRequired) {
                Text(
                    text = "*",
                    color = Red400,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!question.labelEn.isNullOrBlank()) {
            Text(
                text = question.labelEn,
                color = Slate500,
                fontSize = 11.sp
            )
        }

        // Question Input Control
        when (question.dataType) {
            QuestionDataType.TEXT -> {
                TextQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.Text,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.INTEGER -> {
                IntegerQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.Integer,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.DECIMAL -> {
                DecimalQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.Decimal,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.DATE -> {
                DateQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.Date,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.SELECT_ONE -> {
                val rawChoiceList = question.choicesListName?.let { choiceLists[it] }
                val filteredItems = expressionEvaluator.filterChoices(question, rawChoiceList, formState.toExpressionContext())
                val activeChoiceList = rawChoiceList?.copy(items = filteredItems)

                SelectOneQuestionInput(
                    question = question,
                    choiceList = activeChoiceList,
                    currentValue = currentValue as? RuntimeValue.Choice,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.SELECT_MULTIPLE -> {
                val rawChoiceList = question.choicesListName?.let { choiceLists[it] }
                val filteredItems = expressionEvaluator.filterChoices(question, rawChoiceList, formState.toExpressionContext())
                val activeChoiceList = rawChoiceList?.copy(items = filteredItems)

                SelectMultipleQuestionInput(
                    question = question,
                    choiceList = activeChoiceList,
                    currentValue = currentValue as? RuntimeValue.MultipleChoice,
                    onValueChanged = { onValueChanged(question.name, it) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.ADMIN_SELECT -> {
                val parentField = question.adminBinding?.parentField
                val parentPcode = if (parentField != null) {
                    val parentVal = formState.getValue(parentField)
                    when (parentVal) {
                        is RuntimeValue.AdminSelection -> parentVal.pcode
                        is RuntimeValue.Text -> parentVal.value
                        is RuntimeValue.Choice -> parentVal.selectedName
                        else -> null
                    }
                } else null

                AdminSelectQuestionInput(
                    question = question,
                    adminBinding = question.adminBinding,
                    currentValue = currentValue as? RuntimeValue.AdminSelection,
                    onValueChanged = { onValueChanged(question.name, it) },
                    adminLookupProvider = adminLookupProvider,
                    parentPcodeValue = parentPcode,
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.GEOPOINT -> {
                GeopointQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.Geopoint,
                    onCaptureGps = { onCaptureGps?.invoke(question.name) },
                    isReadOnly = isReadOnly
                )
            }
            QuestionDataType.IMAGE -> {
                ImageQuestionInput(
                    question = question,
                    currentValue = currentValue as? RuntimeValue.ImageRef,
                    onCaptureImage = { onCaptureImage?.invoke(question.name) },
                    isReadOnly = isReadOnly
                )
            }
        }

        // Validation Error Message
        if (error != null) {
            Text(
                text = error,
                color = Red400,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// Supporting nested background color
private val Slate850 = androidx.compose.ui.graphics.Color(0xFF172033)
