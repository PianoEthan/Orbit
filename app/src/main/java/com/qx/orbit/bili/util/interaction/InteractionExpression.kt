package com.qx.orbit.bili.util.interaction

import java.util.Collections

object InteractionExpression {
    const val MAX_SOURCE_LENGTH = 16_384
    const val MAX_STATEMENTS = 256
    const val MAX_NESTING_DEPTH = 64

    sealed interface Result

    data class Success(val value: Double, val variables: Map<String, Double>) : Result

    data class Failure(val kind: ErrorKind, val message: String, val position: Int) : Result

    enum class ErrorKind {
        SYNTAX,
        LIMIT_EXCEEDED,
        DIVISION_BY_ZERO,
        NON_FINITE_NUMBER
    }

    fun evaluate(source: String, variables: Map<String, Double> = emptyMap()): Result {
        if (source.length > MAX_SOURCE_LENGTH) {
            return Failure(ErrorKind.LIMIT_EXCEEDED, "Source length exceeds $MAX_SOURCE_LENGTH", MAX_SOURCE_LENGTH)
        }
        val state = variables.toMutableMap()
        val invalidVariable = state.entries.firstOrNull { !it.value.isFinite() }
        if (invalidVariable != null) {
            return Failure(ErrorKind.NON_FINITE_NUMBER, "Variable '${invalidVariable.key}' is not finite", 0)
        }
        return try {
            val value = Parser(source, state).evaluate()
            Success(value, Collections.unmodifiableMap(LinkedHashMap(state)))
        } catch (error: EvaluationException) {
            Failure(error.kind, error.message.orEmpty(), error.position)
        }
    }

    private class EvaluationException(
        val kind: ErrorKind,
        override val message: String,
        val position: Int
    ) : Exception(message)

    private enum class Kind {
        NUMBER, NAME, PLUS, MINUS, MULTIPLY, DIVIDE, NOT,
        EQUAL, NOT_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
        AND, OR, ASSIGN, LEFT_PAREN, RIGHT_PAREN, SEPARATOR, END
    }

    private data class Token(val kind: Kind, val position: Int, val text: String = "")

    private class Lexer(private val source: String) {
        private var position = 0

        fun next(): Token {
            while (position < source.length && source[position] in " \t\r\u000B\u000C") position++
            val start = position
            if (position == source.length) return Token(Kind.END, position)
            val char = source[position++]
            if (char in '0'..'9' || char == '.') {
                while (position < source.length && (source[position] in '0'..'9' || source[position] == '.')) position++
                val text = source.substring(start, position)
                val value = text.toDoubleOrNull()
                    ?: fail(ErrorKind.SYNTAX, "Invalid decimal number '$text'", start)
                if (!value.isFinite()) fail(ErrorKind.NON_FINITE_NUMBER, "Number is not finite", start)
                return Token(Kind.NUMBER, start, text)
            }
            if (isNameStart(char)) {
                while (position < source.length && (isNameStart(source[position]) || source[position] in '0'..'9')) position++
                return Token(Kind.NAME, start, source.substring(start, position))
            }
            val kind = when (char) {
                ';', '\n' -> Kind.SEPARATOR
                '+' -> {
                    if (take('+')) fail(ErrorKind.SYNTAX, "Operator '++' is not supported", start)
                    Kind.PLUS
                }
                '-' -> {
                    if (take('-')) fail(ErrorKind.SYNTAX, "Operator '--' is not supported", start)
                    Kind.MINUS
                }
                '*' -> Kind.MULTIPLY
                '/' -> Kind.DIVIDE
                '!' -> if (take('=')) Kind.NOT_EQUAL else Kind.NOT
                '=' -> if (take('=')) Kind.EQUAL else Kind.ASSIGN
                '>' -> if (take('=')) Kind.GREATER_EQUAL else Kind.GREATER
                '<' -> if (take('=')) Kind.LESS_EQUAL else Kind.LESS
                '&' -> if (take('&')) Kind.AND else fail(ErrorKind.SYNTAX, "Expected '&&'", start)
                '|' -> if (take('|')) Kind.OR else fail(ErrorKind.SYNTAX, "Expected '||'", start)
                '(' -> Kind.LEFT_PAREN
                ')' -> Kind.RIGHT_PAREN
                else -> fail(ErrorKind.SYNTAX, "Unsupported character '$char'", start)
            }
            return Token(kind, start)
        }

        private fun take(char: Char): Boolean {
            if (position == source.length || source[position] != char) return false
            position++
            return true
        }

        private fun isNameStart(char: Char): Boolean =
            char in 'a'..'z' || char in 'A'..'Z' || char == '_' || char == '$'
    }

    private class Parser(source: String, private val variables: MutableMap<String, Double>) {
        private val lexer = Lexer(source)
        private var token = lexer.next()
        private var depth = 0

        fun evaluate(): Double {
            var value = 0.0
            var statements = 0
            while (token.kind != Kind.END) {
                if (token.kind == Kind.SEPARATOR) {
                    advance()
                    continue
                }
                if (++statements > MAX_STATEMENTS) {
                    fail(ErrorKind.LIMIT_EXCEEDED, "Statement count exceeds $MAX_STATEMENTS", token.position)
                }
                value = logical()
                if (token.kind != Kind.SEPARATOR && token.kind != Kind.END) {
                    fail(ErrorKind.SYNTAX, "Expected a statement separator or end of input", token.position)
                }
            }
            return value
        }

        private fun logical(): Double {
            var value = comparison()
            while (token.kind == Kind.AND || token.kind == Kind.OR) {
                val operator = token.kind
                advance()
                val right = comparison()
                value = boolean(if (operator == Kind.AND) value != 0.0 && right != 0.0 else value != 0.0 || right != 0.0)
            }
            return value
        }

        private fun comparison(): Double {
            var value = addition()
            while (token.kind in COMPARISONS) {
                val operator = token.kind
                advance()
                val right = addition()
                value = boolean(when (operator) {
                    Kind.EQUAL -> value == right
                    Kind.NOT_EQUAL -> value != right
                    Kind.GREATER -> value > right
                    Kind.GREATER_EQUAL -> value >= right
                    Kind.LESS -> value < right
                    Kind.LESS_EQUAL -> value <= right
                    else -> error("Unexpected comparison operator")
                })
            }
            return value
        }

        private fun addition(): Double {
            var value = multiplication()
            while (token.kind == Kind.PLUS || token.kind == Kind.MINUS) {
                val operator = token
                advance()
                val right = multiplication()
                value = finite(if (operator.kind == Kind.PLUS) value + right else value - right, operator.position)
            }
            return value
        }

        private fun multiplication(): Double {
            var value = primary()
            while (token.kind == Kind.MULTIPLY || token.kind == Kind.DIVIDE) {
                val operator = token
                advance()
                val right = primary()
                if (operator.kind == Kind.DIVIDE && right == 0.0) {
                    fail(ErrorKind.DIVISION_BY_ZERO, "Division by zero", operator.position)
                }
                value = finite(if (operator.kind == Kind.MULTIPLY) value * right else value / right, operator.position)
            }
            return value
        }

        private fun primary(): Double {
            if (++depth > MAX_NESTING_DEPTH) {
                fail(ErrorKind.LIMIT_EXCEEDED, "Nesting depth exceeds $MAX_NESTING_DEPTH", token.position)
            }
            try {
                val current = token
                return when (current.kind) {
                    Kind.NUMBER -> {
                        advance()
                        current.text.toDouble()
                    }
                    Kind.NAME -> {
                        advance()
                        if (token.kind == Kind.ASSIGN) {
                            advance()
                            val value = logical()
                            variables[current.text] = value
                            value
                        } else {
                            variables.getOrPut(current.text) { 0.0 }
                        }
                    }
                    Kind.PLUS, Kind.MINUS, Kind.NOT -> {
                        advance()
                        val value = primary()
                        when (current.kind) {
                            Kind.MINUS -> -value
                            Kind.NOT -> boolean(value == 0.0)
                            else -> value
                        }
                    }
                    Kind.LEFT_PAREN -> {
                        advance()
                        val value = logical()
                        if (token.kind != Kind.RIGHT_PAREN) {
                            fail(ErrorKind.SYNTAX, "Expected ')'", token.position)
                        }
                        advance()
                        value
                    }
                    else -> fail(ErrorKind.SYNTAX, "Expected a number, variable, unary operator or '('", current.position)
                }
            } finally {
                depth--
            }
        }

        private fun advance() {
            token = lexer.next()
        }
    }

    private val COMPARISONS = setOf(Kind.EQUAL, Kind.NOT_EQUAL, Kind.GREATER, Kind.GREATER_EQUAL, Kind.LESS, Kind.LESS_EQUAL)

    private fun boolean(value: Boolean): Double = if (value) 1.0 else 0.0

    private fun finite(value: Double, position: Int): Double {
        if (!value.isFinite()) fail(ErrorKind.NON_FINITE_NUMBER, "Arithmetic result is not finite", position)
        return value
    }

    private fun fail(kind: ErrorKind, message: String, position: Int): Nothing =
        throw EvaluationException(kind, message, position)
}
