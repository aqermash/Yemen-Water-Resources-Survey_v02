package com.yemen.watersurvey.core.expression

import com.yemen.watersurvey.domain.model.RuntimeValue

/**
 * Neutral, strongly-typed expression evaluation context.
 * Belongs strictly to core.expression — zero Presentation or Compose dependencies.
 *
 * Encapsulates:
 * - Runtime field values via strongly-typed [getFieldValue] returning [RuntimeValue]?
 * - Current field value / name for '.' self-reference resolution
 * - Choice-item properties for choice filter expressions
 * - Immutable [surveyUUID] for deterministic uuid() evaluation
 * - Evaluation [todayDate] for deterministic today() evaluation
 *
 * Phase 13A architectural decoupling per frozen C6.1.2 contract.
 */
data class ExpressionEvaluationContext(
    val getFieldValue: (fieldName: String) -> RuntimeValue? = { null },
    val currentFieldValue: RuntimeValue? = null,
    val currentFieldName: String? = null,
    val itemProperties: Map<String, String>? = null,
    val surveyUUID: String? = null,
    val todayDate: String? = null
) {
    companion object {
        /** Empty context with no values — convenient for tests and default evaluation. */
        val EMPTY: ExpressionEvaluationContext = ExpressionEvaluationContext()
    }
}
