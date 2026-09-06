package com.yemen.watersurvey.presentation.form

import com.yemen.watersurvey.core.expression.ExpressionEvaluationContext
import com.yemen.watersurvey.core.expression.FormExpressionEvaluator
import com.yemen.watersurvey.core.expression.DefaultFormExpressionEvaluator
import com.yemen.watersurvey.domain.model.*

/**
 * Runtime immutable form state representing mutable survey responses.
 *
 * In accordance with C6.1.2:
 * - The FormDefinition is immutable.
 * - DynamicFormState holds the runtime map of fieldName -> RuntimeValue.
 * - Every RuntimeValue instance is itself immutable.
 * - User modifications replace the state with an updated map.
 *
 * Phase 13A: FormExpressionEvaluator interface is defined in core.expression.
 * DynamicFormState provides the [toExpressionContext] adapter to bridge Presentation
 * → core.expression without violating the dependency direction.
 */
data class DynamicFormState(
    val values: Map<String, RuntimeValue> = emptyMap(),
    val validationErrors: Map<String, String> = emptyMap(),
    val isReadOnly: Boolean = false
) {
    fun getValue(fieldName: String): RuntimeValue? = values[fieldName]

    fun getText(fieldName: String): String? = (values[fieldName] as? RuntimeValue.Text)?.value
    fun getInteger(fieldName: String): Long? = (values[fieldName] as? RuntimeValue.Integer)?.value
    fun getDecimal(fieldName: String): Double? = (values[fieldName] as? RuntimeValue.Decimal)?.value
    fun getDate(fieldName: String): String? = (values[fieldName] as? RuntimeValue.Date)?.isoDate
    fun getChoice(fieldName: String): String? = (values[fieldName] as? RuntimeValue.Choice)?.selectedName
    fun getMultipleChoice(fieldName: String): Set<String>? = (values[fieldName] as? RuntimeValue.MultipleChoice)?.selectedNames
    fun getAdminSelection(fieldName: String): RuntimeValue.AdminSelection? = values[fieldName] as? RuntimeValue.AdminSelection
    fun getGeopoint(fieldName: String): RuntimeValue.Geopoint? = values[fieldName] as? RuntimeValue.Geopoint
    fun getImageRef(fieldName: String): RuntimeValue.ImageRef? = values[fieldName] as? RuntimeValue.ImageRef

    fun withValue(fieldName: String, value: RuntimeValue?): DynamicFormState {
        val newValues = if (value != null) {
            values + (fieldName to value)
        } else {
            values - fieldName
        }
        return copy(values = newValues)
    }

    fun withValues(newValues: Map<String, RuntimeValue>): DynamicFormState {
        return copy(values = newValues)
    }

    fun withError(fieldName: String, error: String?): DynamicFormState {
        val newErrors = if (error != null) {
            validationErrors + (fieldName to error)
        } else {
            validationErrors - fieldName
        }
        return copy(validationErrors = newErrors)
    }

    /**
     * Phase 13A adapter: produces a neutral [ExpressionEvaluationContext] from this state.
     * Maps field names directly to [RuntimeValue] domain instances without unwrapping in Presentation.
     *
     * @param surveyUUID immutable UUID for the survey session (freezes uuid() calls)
     * @param todayDate ISO date string for today() calls (nullable = use real today)
     */
    fun toExpressionContext(
        surveyUUID: String? = null,
        todayDate: String? = null
    ): ExpressionEvaluationContext {
        return ExpressionEvaluationContext(
            getFieldValue = { fieldName -> values[fieldName] },
            surveyUUID = surveyUUID,
            todayDate = todayDate
        )
    }
}

/**
 * Decoupled administrative lookup provider for resolving P-code choices dynamically.
 * Keeps administrative datasets completely external to Form Packages.
 */
interface AdminLookupProvider {
    suspend fun getGovernorates(): List<AdminOption>
    suspend fun getDistricts(governoratePcode: String): List<AdminOption>
    suspend fun getUzlahs(districtPcode: String): List<AdminOption>
    suspend fun getVillages(uzlahPcode: String): List<AdminOption>
}

data class AdminOption(
    val pcode: String,
    val nameAr: String,
    val nameEn: String? = null
)
