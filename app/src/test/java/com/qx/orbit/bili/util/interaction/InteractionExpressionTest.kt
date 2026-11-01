package com.qx.orbit.bili.util.interaction

import com.qx.orbit.bili.util.interaction.InteractionExpression.ErrorKind
import com.qx.orbit.bili.util.interaction.InteractionExpression.Failure
import com.qx.orbit.bili.util.interaction.InteractionExpression.Success
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionExpressionTest {
    @Test
    fun arithmeticUsesDecimalDoublesAndLeftAssociativePrecedence() {
        assertValue(7.0, "1+(2*3)")
        assertValue(9.0, "(1+2)*3")
        assertValue(2.0, "8/2/2")
        assertValue(5.0, "10-3-2")
        assertValue(2.75, ".5+1.25+1.")
        assertValue(0.125, "1/8")
        assertValue(-5.0, "-2*+3+1")
        assertValue(2.0, "- -2")
    }

    @Test
    fun comparisonOperatorsSharePrecedenceAndReturnNumericBooleans() {
        for (source in listOf("3==3", "3!=4", "3>2", "3>=3", "2<3", "3<=3", "0==2<1")) {
            assertValue(1.0, source)
        }
        for (source in listOf("3==4", "3!=3", "2>3", "2>=3", "3<2", "3<=2")) {
            assertValue(0.0, source)
        }
        assertValue(1.0, "1+2*3>=7")
        assertValue(1.0, "!0")
        assertValue(0.0, "!-2")
        assertValue(1.0, "!!-2")
    }

    @Test
    fun logicalOperatorsSharePrecedenceAndDoNotShortCircuit() {
        assertValue(0.0, "1||0&&0")
        assertValue(1.0, "1||(0&&0)")
        assertValue(1.0, "-2&&3")
        val and = success("0&&(\$x=7)")
        assertEquals(0.0, and.value, 0.0)
        assertEquals(7.0, and.variables.getValue("\$x"), 0.0)
        val or = success("1||(\$x=8)")
        assertEquals(1.0, or.value, 0.0)
        assertEquals(8.0, or.variables.getValue("\$x"), 0.0)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, failure("0&&(1/0)").kind)
        assertEquals(ErrorKind.DIVISION_BY_ZERO, failure("1||(1/0)").kind)
    }

    @Test
    fun assignmentsAreExpressionsAndStatementsExecuteInOrder() {
        val result = success("\$a=2;\$a=\$a+1;\$a>=3&&\$a!=4")
        assertEquals(1.0, result.value, 0.0)
        assertEquals(3.0, result.variables.getValue("\$a"), 0.0)
        val chained = success("\$a=\$b=2;(\$a=\$a+3)+(\$b=\$a*2)")
        assertEquals(15.0, chained.value, 0.0)
        assertEquals(mapOf("\$a" to 5.0, "\$b" to 10.0), chained.variables)
        val primaryAssignment = success("2*\$a=3+4")
        assertEquals(14.0, primaryAssignment.value, 0.0)
        assertEquals(7.0, primaryAssignment.variables.getValue("\$a"), 0.0)
        val lines = success(";\n\$a=1\r\n\$b=\$a+2;\n\n\$a=\$b*2;;\n")
        assertEquals(6.0, lines.value, 0.0)
        assertEquals(mapOf("\$a" to 6.0, "\$b" to 3.0), lines.variables)
    }

    @Test
    fun unknownNamesInitializeToZeroAndNamesRetainTheirExactSpelling() {
        val result = success("\$unset+_score2+plain9+\$A_b1")
        assertEquals(0.0, result.value, 0.0)
        assertEquals(mapOf("\$unset" to 0.0, "_score2" to 0.0, "plain9" to 0.0, "\$A_b1" to 0.0), result.variables)
        assertValue(2.0, "\$unset+2")
        val caseSensitive = success("Score=2;score=3;Score+score")
        assertEquals(5.0, caseSensitive.value, 0.0)
        assertEquals(2, caseSensitive.variables.size)
    }

    @Test
    fun successfulEvaluationReturnsAnIndependentSnapshotWithoutChangingInput() {
        val input = mutableMapOf("\$a" to 1.0, "untouched" to -2.5)
        val result = success("\$a=3;\$new+\$a", input)
        assertEquals(3.0, result.value, 0.0)
        assertEquals(mapOf("\$a" to 1.0, "untouched" to -2.5), input)
        assertEquals(mapOf("\$a" to 3.0, "untouched" to -2.5, "\$new" to 0.0), result.variables)
        assertNotSame(input, result.variables)
        input["\$a"] = 99.0
        assertEquals(3.0, result.variables.getValue("\$a"), 0.0)
        val next = success("\$a=5", result.variables)
        assertEquals(5.0, next.variables.getValue("\$a"), 0.0)
        assertEquals(3.0, result.variables.getValue("\$a"), 0.0)
    }

    @Test
    fun emptyProgramsAreZeroValuedNoOpsNotTrueConditions() {
        val input = mutableMapOf("\$a" to 2.0)
        for (source in listOf("", " \t\r", ";\n;;\n")) {
            val result = success(source, input)
            assertEquals(0.0, result.value, 0.0)
            assertEquals(input, result.variables)
            assertNotSame(input, result.variables)
        }
    }

    @Test
    fun unsupportedSyntaxAndTrailingInputNeverPartiallySucceed() {
        for (source in listOf(
            "2%1", "2**3", "1===1", "1!==2", "\$a+=1", "\$a-=1", "++\$a", "\$a++", "\$a--",
            "fn(1)", "\$a.member", "'text'", "\"text\"", "1?2:3", "1,2", "1&2", "1|2",
            "1e2", "0x10", "1 2", "\$a \$b", "1;2@3", "1//2", "1/*x*/2", "1\u00002"
        )) {
            assertEquals(source, ErrorKind.SYNTAX, failure(source).kind)
        }
    }

    @Test
    fun malformedSyntaxIsDiagnosableAndParenthesesMustBalance() {
        for (source in listOf(".", "1..2", "1.2.3", "(", ")", "()", "(1", "1)", "(1+2]", "(1;2)", "1+", "*2", "=1", "\$a=", "1=2", "(\$a)=2", "1&&")) {
            val result = failure(source)
            assertEquals(source, ErrorKind.SYNTAX, result.kind)
            assertTrue(source, result.message.isNotBlank())
            assertTrue(source, result.position in 0..source.length)
        }
        assertEquals(2, failure("1+)").position)
    }

    @Test
    fun failuresNeverCommitAssignmentsOrUnknownVariableInitialization() {
        val input = mutableMapOf("\$a" to 2.0)
        for (source in listOf("\$a=7;\$new;1/0", "\$a=7;\$new;2@3", "\$a=(\$new=3)+", "\$a=7;" + "(".repeat(80) + "1")) {
            failure(source, input)
            assertEquals(mapOf("\$a" to 2.0), input)
            assertFalse(input.containsKey("\$new"))
        }
    }

    @Test
    fun divisionByZeroAndNonFiniteNumbersFailExplicitly() {
        for (source in listOf("1/0", "0/0", "1/-0", "1/(2-2)")) {
            assertEquals(source, ErrorKind.DIVISION_BY_ZERO, failure(source).kind)
        }
        val large = "9".repeat(200)
        for (source in listOf("9".repeat(400), "$large*$large")) {
            assertEquals(ErrorKind.NON_FINITE_NUMBER, failure(source).kind)
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(ErrorKind.NON_FINITE_NUMBER, failure("1", mapOf("\$a" to value)).kind)
        }
    }

    @Test
    fun resourceLimitsRejectExcessiveLengthStatementCountAndEveryRecursiveForm() {
        assertValue(1.0, " ".repeat(InteractionExpression.MAX_SOURCE_LENGTH - 1) + "1")
        assertEquals(ErrorKind.LIMIT_EXCEEDED, failure(" ".repeat(InteractionExpression.MAX_SOURCE_LENGTH + 1)).kind)
        assertValue(1.0, List(InteractionExpression.MAX_STATEMENTS) { "1" }.joinToString(";"))
        assertEquals(ErrorKind.LIMIT_EXCEEDED, failure(List(InteractionExpression.MAX_STATEMENTS + 1) { "1" }.joinToString("\n")).kind)
        val depth = InteractionExpression.MAX_NESTING_DEPTH + 1
        for (source in listOf("(".repeat(depth) + "1" + ")".repeat(depth), "!".repeat(depth) + "1", "\$a=".repeat(depth) + "1")) {
            assertEquals(ErrorKind.LIMIT_EXCEEDED, failure(source).kind)
        }
        assertValue(1.0, "(".repeat(20) + "1" + ")".repeat(20))
        assertValue(1000.0, List(1000) { "1" }.joinToString("+"))
    }

    @Test
    fun evaluatesLiveNativeActionAndDocumentedChoiceCondition() {
        val input = mapOf("\$lMQqQ994Sk" to 0.0, "\$Zh4JACIiId" to 4.0)
        val action = success("\$lMQqQ994Sk=3.00;\$Zh4JACIiId=\$Zh4JACIiId+1.00", input)
        assertEquals(5.0, action.value, 0.0)
        assertEquals(mapOf("\$lMQqQ994Sk" to 3.0, "\$Zh4JACIiId" to 5.0), action.variables)
        val condition = "\$H7g_64_PG2EVS>=1.00 && \$H7g_64_PG2EVS<=80.00"
        for (value in listOf(1.0, 40.5, 80.0)) {
            assertEquals(1.0, success(condition, mapOf("\$H7g_64_PG2EVS" to value)).value, 0.0)
        }
        for (value in listOf(0.0, 80.01, 100.0)) {
            assertEquals(0.0, success(condition, mapOf("\$H7g_64_PG2EVS" to value)).value, 0.0)
        }
    }

    private fun assertValue(expected: Double, source: String) {
        assertEquals(source, expected, success(source).value, 0.0)
    }

    private fun success(source: String, variables: Map<String, Double> = emptyMap()): Success {
        val result = InteractionExpression.evaluate(source, variables)
        assertTrue("Expected success for '$source', got $result", result is Success)
        return result as Success
    }

    private fun failure(source: String, variables: Map<String, Double> = emptyMap()): Failure {
        val result = InteractionExpression.evaluate(source, variables)
        assertTrue("Expected failure for '$source', got $result", result is Failure)
        return result as Failure
    }
}
