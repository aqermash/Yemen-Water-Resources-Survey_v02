package com.yemen.watersurvey.core.expression

import com.yemen.watersurvey.domain.model.*

/**
 * Authoritative Phase 13 FormExpressionEvaluator implementation.
 *
 * Phase 13A architectural decoupling:
 * - Zero imports from presentation.* or androidx.*
 * - All evaluation requests operate through [ExpressionEvaluationContext]
 * - Preserves exact Phase 13 expression grammar and evaluation semantics
 *
 * Evaluates:
 * - Relevance / Visibility expressions
 * - In-memory CalculateElement calculations
 * - QuestionElement constraint messages
 * - Choice list filtering (choice_filter)
 */
class FormExpressionEvaluatorImpl(
    private val engine: ExpressionEvaluatorEngine = ExpressionEvaluatorEngine(),
    private val surveyUUID: String? = null,
    private val todayDate: String? = null
) : FormExpressionEvaluator {

    override fun isElementRelevant(element: FormElement, context: ExpressionEvaluationContext): Boolean {
        val expr = element.relevantExpr ?: return true
        if (expr.rawExpression.isBlank()) return true

        return try {
            val ast = parseExpressionString(expr.rawExpression)
            val evalContext = context.copy(
                surveyUUID = context.surveyUUID ?: surveyUUID,
                todayDate = context.todayDate ?: todayDate
            )
            val result = engine.evaluate(ast, evalContext)
            engine.toBoolean(result)
        } catch (e: Exception) {
            // Fail-safe default: treat element as relevant if expression fails
            true
        }
    }

    override fun evaluateCalculation(element: CalculateElement, context: ExpressionEvaluationContext): RuntimeValue? {
        val expr = element.calculationExpr
        if (expr.rawExpression.isBlank()) return null

        return try {
            val ast = parseExpressionString(expr.rawExpression)
            val evalContext = context.copy(
                surveyUUID = context.surveyUUID ?: surveyUUID,
                todayDate = context.todayDate ?: todayDate
            )
            val result = engine.evaluate(ast, evalContext)
            when (result) {
                is RuntimeValue -> result
                is Long -> RuntimeValue.Integer(result)
                is Int -> RuntimeValue.Integer(result.toLong())
                is Double -> RuntimeValue.Decimal(result)
                is Float -> RuntimeValue.Decimal(result.toDouble())
                is String -> if (result.isNotBlank()) RuntimeValue.Text(result) else null
                else -> result?.let { RuntimeValue.Text(it.toString()) }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun validateConstraint(
        element: QuestionElement,
        value: RuntimeValue?,
        context: ExpressionEvaluationContext
    ): String? {
        val expr = element.constraintExpr ?: return null
        if (expr.rawExpression.isBlank()) return null
        if (value == null) return null // Empty value is handled by isRequired gate

        return try {
            val ast = parseExpressionString(expr.rawExpression)
            val evalContext = context.copy(
                currentFieldName = element.name,
                currentFieldValue = value,
                surveyUUID = context.surveyUUID ?: surveyUUID,
                todayDate = context.todayDate ?: todayDate
            )
            val result = engine.evaluate(ast, evalContext)
            val isSatisfied = engine.toBoolean(result)
            if (isSatisfied) null else (element.constraintMessageAr ?: "القيمة المدخلة غير صالحة")
        } catch (e: Exception) {
            null
        }
    }

    override fun filterChoices(
        element: QuestionElement,
        choiceList: ChoiceList?,
        context: ExpressionEvaluationContext
    ): List<ChoiceItem> {
        val expr = element.choiceFilterExpr ?: return choiceList?.items ?: emptyList()
        if (expr.rawExpression.isBlank() || choiceList == null) return choiceList?.items ?: emptyList()

        return try {
            val ast = parseExpressionString(expr.rawExpression)
            choiceList.items.filter { item ->
                val districtPcode = if (item.name.length >= 6) item.name.substring(0, 6) else item.name
                val provincePcode = if (item.name.length >= 4) item.name.substring(0, 4) else item.name
                val itemProps = mapOf(
                    "name" to item.name,
                    "labelAr" to item.labelAr,
                    "district_code" to districtPcode,
                    "province_code" to provincePcode,
                    "gov_pcode" to provincePcode,
                    "uzlah_code" to item.name
                )
                val evalContext = context.copy(
                    itemProperties = itemProps,
                    surveyUUID = context.surveyUUID ?: surveyUUID,
                    todayDate = context.todayDate ?: todayDate
                )
                val res = engine.evaluate(ast, evalContext)
                engine.toBoolean(res)
            }
        } catch (e: Exception) {
            choiceList.items
        }
    }

    private fun parseExpressionString(rawExpression: String): AstNode {
        val tokenizer = Tokenizer(rawExpression)
        val tokens = tokenizer.tokenize()
        val parser = ExpressionParser(tokens)
        return parser.parse()
    }
}
