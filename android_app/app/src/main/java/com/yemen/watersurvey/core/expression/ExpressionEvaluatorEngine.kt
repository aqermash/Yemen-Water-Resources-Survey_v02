package com.yemen.watersurvey.core.expression

import com.yemen.watersurvey.domain.model.RuntimeValue
import java.text.SimpleDateFormat
import java.util.*

/**
 * Core engine for evaluating parsed AST nodes against an [ExpressionEvaluationContext].
 *
 * Phase 13A architectural decoupling:
 * - Zero imports from presentation.* or androidx.*
 * - Consistently operates on [ExpressionEvaluationContext]
 * - Canonical [unwrapRuntimeValue] converts strongly typed [RuntimeValue] to evaluation primitives
 */
class ExpressionEvaluatorEngine {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun evaluate(node: AstNode, context: ExpressionEvaluationContext = ExpressionEvaluationContext()): Any? {
        return when (node) {
            is AstNode.Literal -> node.value

            is AstNode.FieldReference -> {
                val rv = context.getFieldValue(node.fieldName)
                unwrapRuntimeValue(rv)
            }

            is AstNode.ItemProperty -> {
                if (context.itemProperties != null && context.itemProperties.containsKey(node.propertyName)) {
                    context.itemProperties[node.propertyName]
                } else {
                    val rv = context.getFieldValue(node.propertyName)
                    unwrapRuntimeValue(rv)
                }
            }

            is AstNode.SelfReference -> {
                if (context.currentFieldValue != null) {
                    unwrapRuntimeValue(context.currentFieldValue)
                } else if (context.currentFieldName != null) {
                    val rv = context.getFieldValue(context.currentFieldName)
                    unwrapRuntimeValue(rv)
                } else {
                    ""
                }
            }

            is AstNode.BinaryOperation -> evaluateBinary(node, context)

            is AstNode.FunctionCall -> evaluateFunction(node, context)
        }
    }

    fun unwrapRuntimeValue(rv: RuntimeValue?): Any? {
        if (rv == null) return ""
        return when (rv) {
            is RuntimeValue.Text -> rv.value
            is RuntimeValue.Integer -> rv.value
            is RuntimeValue.Decimal -> rv.value
            is RuntimeValue.Date -> rv.isoDate
            is RuntimeValue.Choice -> rv.selectedName
            is RuntimeValue.MultipleChoice -> rv.selectedNames
            is RuntimeValue.AdminSelection -> rv.pcode
            is RuntimeValue.Geopoint -> "${rv.latitude},${rv.longitude}"
            is RuntimeValue.ImageRef -> rv.attachmentId
        }
    }

    private fun evaluateBinary(
        node: AstNode.BinaryOperation,
        context: ExpressionEvaluationContext
    ): Any? {
        // Short-circuiting logical operations
        if (node.operator == BinaryOperator.AND) {
            val leftVal = evaluate(node.left, context)
            if (!toBoolean(leftVal)) return false
            val rightVal = evaluate(node.right, context)
            return toBoolean(rightVal)
        }

        if (node.operator == BinaryOperator.OR) {
            val leftVal = evaluate(node.left, context)
            if (toBoolean(leftVal)) return true
            val rightVal = evaluate(node.right, context)
            return toBoolean(rightVal)
        }

        val left = evaluate(node.left, context)
        val right = evaluate(node.right, context)

        return when (node.operator) {
            BinaryOperator.EQ -> compareEqual(left, right)
            BinaryOperator.NEQ -> !compareEqual(left, right)
            BinaryOperator.GT -> compareNumericOrString(left, right) > 0
            BinaryOperator.GTE -> compareNumericOrString(left, right) >= 0
            BinaryOperator.LTE -> compareNumericOrString(left, right) <= 0
            BinaryOperator.MUL -> multiply(left, right)
            BinaryOperator.DIV -> divide(left, right)
            else -> false
        }
    }

    private fun evaluateFunction(
        node: AstNode.FunctionCall,
        context: ExpressionEvaluationContext
    ): Any? {
        return when (node.name.lowercase()) {
            "if" -> {
                if (node.arguments.size < 3) return ""
                val condition = toBoolean(evaluate(node.arguments[0], context))
                if (condition) {
                    evaluate(node.arguments[1], context)
                } else {
                    evaluate(node.arguments[2], context)
                }
            }
            "concat" -> {
                val sb = StringBuilder()
                for (arg in node.arguments) {
                    val evaluated = evaluate(arg, context)
                    if (evaluated != null) {
                        sb.append(evaluated.toString())
                    }
                }
                sb.toString()
            }
            "uuid" -> {
                context.surveyUUID ?: UUID.randomUUID().toString()
            }
            "today" -> {
                context.todayDate ?: isoDateFormat.format(Date())
            }
            "selected" -> {
                if (node.arguments.size < 2) return false
                val container = evaluate(node.arguments[0], context)
                val targetChoice = evaluate(node.arguments[1], context)?.toString() ?: ""
                when (container) {
                    is Set<*> -> container.contains(targetChoice)
                    is Collection<*> -> container.contains(targetChoice)
                    is String -> container == targetChoice
                    else -> false
                }
            }
            "count-selected" -> {
                if (node.arguments.isEmpty()) return 0L
                val container = evaluate(node.arguments[0], context)
                when (container) {
                    is Set<*> -> container.size.toLong()
                    is Collection<*> -> container.size.toLong()
                    is String -> if (container.isNotBlank()) 1L else 0L
                    else -> 0L
                }
            }
            else -> ""
        }
    }

    fun toBoolean(valAny: Any?): Boolean {
        if (valAny == null) return false
        return when (valAny) {
            is Boolean -> valAny
            is Number -> valAny.toDouble() != 0.0
            is String -> valAny.isNotBlank() && valAny.lowercase() != "false" && valAny != "0"
            is Set<*> -> valAny.isNotEmpty()
            is Collection<*> -> valAny.isNotEmpty()
            else -> true
        }
    }

    private fun compareEqual(left: Any?, right: Any?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false

        // Numeric comparison
        val leftNum = toDoubleOrNull(left)
        val rightNum = toDoubleOrNull(right)
        if (leftNum != null && rightNum != null) {
            return leftNum == rightNum
        }

        // Set / Collection check
        if (left is Set<*> && right is String) {
            return left.contains(right)
        }

        return left.toString() == right.toString()
    }

    private fun compareNumericOrString(left: Any?, right: Any?): Int {
        val leftNum = toDoubleOrNull(left)
        val rightNum = toDoubleOrNull(right)
        if (leftNum != null && rightNum != null) {
            return leftNum.compareTo(rightNum)
        }
        val leftStr = left?.toString() ?: ""
        val rightStr = right?.toString() ?: ""
        return leftStr.compareTo(rightStr)
    }

    private fun multiply(left: Any?, right: Any?): Any {
        val leftNum = toDoubleOrNull(left) ?: 0.0
        val rightNum = toDoubleOrNull(right) ?: 0.0
        val result = leftNum * rightNum
        return if (result == result.toLong().toDouble()) result.toLong() else result
    }

    private fun divide(left: Any?, right: Any?): Any {
        val leftNum = toDoubleOrNull(left) ?: 0.0
        val rightNum = toDoubleOrNull(right) ?: 0.0
        if (rightNum == 0.0) return "" // Deterministic division by zero
        val result = leftNum / rightNum
        return if (result == result.toLong().toDouble()) result.toLong() else result
    }

    private fun toDoubleOrNull(value: Any?): Double? {
        if (value == null) return null
        if (value is Number) return value.toDouble()
        val str = value.toString().trim()
        if (str.isEmpty()) return null
        return str.toDoubleOrNull()
    }
}
