package com.yemen.watersurvey.core.expression

enum class TokenType {
    FIELD_REF,       // ${field_name}
    SELF_REF,        // .
    STRING_LITERAL,  // 'text'
    NUMBER_LITERAL,  // 100, 12.5
    IDENTIFIER,      // if, concat, uuid, today, selected, count-selected, name, etc.
    KEYWORD_AND,     // and
    KEYWORD_OR,      // or
    KEYWORD_DIV,     // div
    OP_EQ,           // =
    OP_NEQ,          // !=
    OP_GT,           // >
    OP_GTE,          // >=
    OP_LTE,          // <=
    OP_MUL,          // *
    LPAREN,          // (
    RPAREN,          // )
    COMMA,           // ,
    UNSUPPORTED      // <, +, /, etc.
}

data class Token(
    val type: TokenType,
    val text: String,
    val position: Int
)

class ExpressionTokenizerException(message: String) : Exception(message)

/**
 * Tokenizer for XLSForm expressions verified in Phase 13.
 * Strict tokenization of allowed inventory:
 * ${field}, ., strings, numbers, keywords (and, or, div),
 * operators (=, !=, >, >=, <=, *), functions (if, concat, uuid, today, selected, count-selected),
 * parentheses and commas.
 */
class Tokenizer(private val input: String) {
    private var index = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (index < input.length) {
            val ch = input[index]

            if (ch.isWhitespace()) {
                index++
                continue
            }

            val startPos = index

            // 1. ${field_name}
            if (ch == '$' && peek(1) == '{') {
                index += 2
                val sb = StringBuilder()
                while (index < input.length && input[index] != '}') {
                    sb.append(input[index])
                    index++
                }
                if (index < input.length && input[index] == '}') {
                    index++
                } else {
                    throw ExpressionTokenizerException("Unclosed field reference \${...} at position $startPos")
                }
                tokens.add(Token(TokenType.FIELD_REF, sb.toString().trim(), startPos))
                continue
            }

            // 2. String literal '...' or "..."
            if (ch == '\'' || ch == '"') {
                val quote = ch
                index++
                val sb = StringBuilder()
                while (index < input.length && input[index] != quote) {
                    sb.append(input[index])
                    index++
                }
                if (index < input.length && input[index] == quote) {
                    index++
                } else {
                    throw ExpressionTokenizerException("Unclosed string literal at position $startPos")
                }
                tokens.add(Token(TokenType.STRING_LITERAL, sb.toString(), startPos))
                continue
            }

            // 3. Self reference '.' (standalone or before operators/whitespace)
            if (ch == '.' && (index + 1 >= input.length || !input[index + 1].isDigit())) {
                index++
                tokens.add(Token(TokenType.SELF_REF, ".", startPos))
                continue
            }

            // 4. Numbers (e.g. 100, 0, 1900, 12.5)
            if (ch.isDigit() || (ch == '.' && peek(1).isDigit())) {
                val sb = StringBuilder()
                while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                    sb.append(input[index])
                    index++
                }
                tokens.add(Token(TokenType.NUMBER_LITERAL, sb.toString(), startPos))
                continue
            }

            // 5. Operators
            if (ch == '!') {
                if (peek(1) == '=') {
                    index += 2
                    tokens.add(Token(TokenType.OP_NEQ, "!=", startPos))
                    continue
                }
            }
            if (ch == '=') {
                index++
                tokens.add(Token(TokenType.OP_EQ, "=", startPos))
                continue
            }
            if (ch == '>') {
                if (peek(1) == '=') {
                    index += 2
                    tokens.add(Token(TokenType.OP_GTE, ">=", startPos))
                } else {
                    index++
                    tokens.add(Token(TokenType.OP_GT, ">", startPos))
                }
                continue
            }
            if (ch == '<') {
                if (peek(1) == '=') {
                    index += 2
                    tokens.add(Token(TokenType.OP_LTE, "<=", startPos))
                } else {
                    // Unsupported standalone '<'
                    index++
                    tokens.add(Token(TokenType.UNSUPPORTED, "<", startPos))
                }
                continue
            }
            if (ch == '*') {
                index++
                tokens.add(Token(TokenType.OP_MUL, "*", startPos))
                continue
            }
            if (ch == '(') {
                index++
                tokens.add(Token(TokenType.LPAREN, "(", startPos))
                continue
            }
            if (ch == ')') {
                index++
                tokens.add(Token(TokenType.RPAREN, ")", startPos))
                continue
            }
            if (ch == ',') {
                index++
                tokens.add(Token(TokenType.COMMA, ",", startPos))
                continue
            }

            // Check for unsupported characters +, /, arithmetic -
            if (ch == '+' || ch == '/') {
                tokens.add(Token(TokenType.UNSUPPORTED, ch.toString(), startPos))
                index++
                continue
            }
            // Check for arithmetic '-' (negative number or minus operator)
            if (ch == '-') {
                // In v6 expressions, '-' appears in strings or format concatenation, or in negative numbers if immediately preceding digits
                if (peek(1).isDigit() && (tokens.isEmpty() || tokens.last().type in listOf(TokenType.OP_EQ, TokenType.OP_NEQ, TokenType.OP_GT, TokenType.OP_GTE, TokenType.OP_LTE, TokenType.LPAREN, TokenType.COMMA))) {
                    index++
                    val sb = StringBuilder("-")
                    while (index < input.length && (input[index].isDigit() || input[index] == '.')) {
                        sb.append(input[index])
                        index++
                    }
                    tokens.add(Token(TokenType.NUMBER_LITERAL, sb.toString(), startPos))
                    continue
                } else {
                    tokens.add(Token(TokenType.UNSUPPORTED, "-", startPos))
                    index++
                    continue
                }
            }

            // 6. Identifiers / Keywords (e.g. if, concat, uuid, today, selected, count-selected, and, or, div, name, etc.)
            if (ch.isLetter() || ch == '_') {
                val sb = StringBuilder()
                while (index < input.length && (input[index].isLetterOrDigit() || input[index] == '_' || input[index] == '-')) {
                    sb.append(input[index])
                    index++
                }
                val text = sb.toString()
                val tokenType = when (text.lowercase()) {
                    "and" -> TokenType.KEYWORD_AND
                    "or" -> TokenType.KEYWORD_OR
                    "div" -> TokenType.KEYWORD_DIV
                    else -> TokenType.IDENTIFIER
                }
                tokens.add(Token(tokenType, text, startPos))
                continue
            }

            throw ExpressionTokenizerException("Unexpected character '$ch' at position $index")
        }
        return tokens
    }

    private fun peek(offset: Int): Char {
        return if (index + offset < input.length) input[index + offset] else '\u0000'
    }
}
