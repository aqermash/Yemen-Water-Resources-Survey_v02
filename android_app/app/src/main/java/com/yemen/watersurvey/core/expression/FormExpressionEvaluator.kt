package com.yemen.watersurvey.core.expression

import com.yemen.watersurvey.domain.model.*

/**
 * Core contract for expression evaluation in the Dynamic Form Engine.
 *
 * Defined in core.expression — completely independent of Presentation/UI.
 * DynamicFormState (Presentation) implements this via an adapter, keeping
 * the dependency direction:
 *
 *   Presentation → core.expression ← Expression Engine
 *
 * NOT:
 *   core.expression → Presentation  ← (VIOLATION, now eliminated)
 *
 * Phase 13A architectural fix per C6.1.2 frozen contract.
 */
interface FormExpressionEvaluator {
    fun isElementRelevant(element: FormElement, context: ExpressionEvaluationContext): Boolean = true
    fun evaluateCalculation(element: CalculateElement, context: ExpressionEvaluationContext): RuntimeValue? = null
    fun validateConstraint(element: QuestionElement, value: RuntimeValue?, context: ExpressionEvaluationContext): String? = null
    fun filterChoices(element: QuestionElement, choiceList: ChoiceList?, context: ExpressionEvaluationContext): List<ChoiceItem> {
        return choiceList?.items ?: emptyList()
    }
}

/**
 * Default no-op evaluator: all elements visible, no calculations, no filtering.
 * Used as safe default in Phase 12B when no engine is wired.
 */
object DefaultFormExpressionEvaluator : FormExpressionEvaluator
