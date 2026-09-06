package com.yemen.watersurvey.core.expression

enum class BinaryOperator {
    EQ,   // =
    NEQ,  // !=
    GT,   // >
    GTE,  // >=
    LTE,  // <=
    AND,  // and
    OR,   // or
    MUL,  // *
    DIV   // div
}

sealed interface AstNode {
    data class Literal(val value: Any?) : AstNode
    data class FieldReference(val fieldName: String) : AstNode
    data class ItemProperty(val propertyName: String) : AstNode
    data class SelfReference(val symbol: String = ".") : AstNode
    data class FunctionCall(val name: String, val arguments: List<AstNode>) : AstNode
    data class BinaryOperation(val left: AstNode, val operator: BinaryOperator, val right: AstNode) : AstNode
}

class ExpressionParserException(message: String) : Exception(message)

/**
 * Recursive-Descent Parser for verified Phase 13 XLSForm expressions.
 *
 * Implements strict operator precedence:
 * Level 1: or
 * Level 2: and
 * Level 3: =, !=, >, >=, <=
 * Level 4: *, div
 * Level 5: Primary (Literals, ${field}, ., Function calls, Parentheses)
 *
 * Explicitly rejects unsupported operators (<, +, /, arithmetic -).
 */
class ExpressionParser(private val tokens: List<Token>) {
    private var current = 0

    companion object {
        val VERIFIED_FUNCTIONS = setOf("if", "concat", "uuid", "today", "selected", "count-selected")
    }

    fun parse(): AstNode {
        if (tokens.isEmpty()) {
            return AstNode.Literal("")
        }
        val ast = parseOr()
        if (!isAtEnd()) {
            val unparsedToken = peek()
            throw ExpressionParserException("Unexpected token '${unparsedToken.text}' at position ${unparsedToken.position}")
        }
        return ast
    }

    // Level 1: OR
    private fun parseOr(): AstNode {
        var node = parseAnd()
        while (match(TokenType.KEYWORD_OR)) {
            val right = parseAnd()
            node = AstNode.BinaryOperation(node, BinaryOperator.OR, right)
        }
        return node
    }

    // Level 2: AND
    private fun parseAnd(): AstNode {
        var node = parseComparison()
        while (match(TokenType.KEYWORD_AND)) {
            val right = parseComparison()
            node = AstNode.BinaryOperation(node, BinaryOperator.AND, right)
        }
        return node
    }

    // Level 3: Comparisons (=, !=, >, >=, <=)
    private fun parseComparison(): AstNode {
        var node = parseMultiplicative()
        while (true) {
            val op = when {
                match(TokenType.OP_EQ) -> BinaryOperator.EQ
                match(TokenType.OP_NEQ) -> BinaryOperator.NEQ
                match(TokenType.OP_GTE) -> BinaryOperator.GTE
                match(TokenType.OP_LTE) -> BinaryOperator.LTE
                match(TokenType.OP_GT) -> BinaryOperator.GT
                else -> break
            }
            val right = parseMultiplicative()
            node = AstNode.BinaryOperation(node, op, right)
        }
        return node
    }

    // Level 4: Multiplicative (*, div)
    private fun parseMultiplicative(): AstNode {
        var node = parsePrimary()
        while (true) {
            val op = when {
                match(TokenType.OP_MUL) -> BinaryOperator.MUL
                match(TokenType.KEYWORD_DIV) -> BinaryOperator.DIV
                else -> break
            }
            val right = parsePrimary()
            node = AstNode.BinaryOperation(node, op, right)
        }
        return node
    }

    // Level 5: Primary
    private fun parsePrimary(): AstNode {
        if (isAtEnd()) {
            throw ExpressionParserException("Unexpected end of expression")
        }

        val token = advance()

        when (token.type) {
            TokenType.UNSUPPORTED -> {
                throw ExpressionParserException("Unsupported operator or construct '${token.text}' at position ${token.position}")
            }
            TokenType.FIELD_REF -> {
                return AstNode.FieldReference(token.text)
            }
            TokenType.SELF_REF -> {
                return AstNode.SelfReference()
            }
            TokenType.STRING_LITERAL -> {
                return AstNode.Literal(token.text)
            }
            TokenType.NUMBER_LITERAL -> {
                val numDouble = token.text.toDoubleOrNull()
                val numLong = token.text.toLongOrNull()
                val valNum: Any? = if (numLong != null && numLong.toDouble() == numDouble) numLong else numDouble
                return AstNode.Literal(valNum)
            }
            TokenType.LPAREN -> {
                val expr = parseOr()
                consume(TokenType.RPAREN, "Expected ')' after parenthesized expression")
                return expr
            }
            TokenType.IDENTIFIER -> {
                if (match(TokenType.LPAREN)) {
                    val fnName = token.text.lowercase()
                    if (fnName !in VERIFIED_FUNCTIONS) {
                        throw ExpressionParserException("Unknown or unsupported function '$fnName' at position ${token.position}")
                    }
                    val args = mutableListOf<AstNode>()
                    if (!check(TokenType.RPAREN)) {
                        do {
                            args.add(parseOr())
                        } while (match(TokenType.COMMA))
                    }
                    consume(TokenType.RPAREN, "Expected ')' after function arguments for $fnName")
                    return AstNode.FunctionCall(fnName, args)
                } else {
                    // Plain identifier (e.g. name, district_code in choice filter expressions)
                    return AstNode.ItemProperty(token.text)
                }
            }
            else -> {
                throw ExpressionParserException("Unexpected token '${token.text}' at position ${token.position}")
            }
        }
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean {
        return current >= tokens.size
    }

    private fun peek(): Token {
        return tokens[current]
    }

    private fun previous(): Token {
        return tokens[current - 1]
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ExpressionParserException("$message (found '${peek().text}' at position ${peek().position})")
    }
}
