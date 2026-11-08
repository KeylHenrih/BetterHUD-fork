package kr.toxicity.hud.test

import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.equation.TEquation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.*

class PowerOperatorTranspilerTest {

    private fun bhPow(b: Double, e: Double): Double {
        if (b == 0.0) return 0.0
        val isInt = floor(e) == e
        if (isInt) {
            val isEven = floor(e * 0.5) * 2.0 == e
            return if (isEven) {
                b.absoluteValue.pow(e)
            } else {
                b.sign * b.absoluteValue.pow(e)
            }
        }
        return if (b >= 0.0) b.pow(e) else 0.0
    }

    /**
     * Evaluates transpiled GLSL expression string for a given value of t.
     * Supports:
     * - old pattern: pow(max(0.0, base), exp)
     * - inlined multiplication: ((base) * (base)...)
     * - inlined division: (1.0 / (abs(base) < 1e-6 ? 1e-6 : (base)))
     * - new pattern: bh_pow(base, exp)
     * - math functions: abs(t), min, max, clamp
     */
    private fun evalGlsl(glsl: String, t: Double): Double {
        var s = glsl.replace(" ", "")

        // Replace t with numeric value
        // Note: replace variable 't' not surrounded by alphanumeric/identifier chars
        s = s.replace(Regex("(?<![a-zA-Z0-9_])t(?![a-zA-Z0-9_])"), "(${String.format(java.util.Locale.US, "%.6f", t)})")

        fun evalNode(str: String): Double {
            var expr = str.trim()
            while (expr.startsWith("(") && expr.endsWith(")") && isMatchingOuterParens(expr)) {
                expr = expr.substring(1, expr.length - 1).trim()
            }

            // Check ternary: cond ? a : b
            val qIndex = findTernaryQuestion(expr)
            if (qIndex != -1) {
                val colonIndex = findMatchingColon(expr, qIndex)
                if (colonIndex != -1) {
                    val cond = expr.substring(0, qIndex)
                    val trueBranch = expr.substring(qIndex + 1, colonIndex)
                    val falseBranch = expr.substring(colonIndex + 1)
                    val condVal = if ("<" in cond) {
                        val parts = cond.split("<")
                        evalNode(parts[0]) < evalNode(parts[1])
                    } else false
                    return if (condVal) evalNode(trueBranch) else evalNode(falseBranch)
                }
            }

            // Check binary +, - (lowest precedence)
            var opIdx = findLowestPrecedenceOp(expr, listOf("+", "-"))
            if (opIdx != -1) {
                val op = expr[opIdx]
                // Check if unary minus at beginning
                if (opIdx == 0 && op == '-') {
                    return -evalNode(expr.substring(1))
                }
                val left = evalNode(expr.substring(0, opIdx))
                val right = evalNode(expr.substring(opIdx + 1))
                return if (op == '+') left + right else left - right
            }

            // Check binary *, /
            opIdx = findLowestPrecedenceOp(expr, listOf("*", "/"))
            if (opIdx != -1) {
                val op = expr[opIdx]
                val left = evalNode(expr.substring(0, opIdx))
                val right = evalNode(expr.substring(opIdx + 1))
                return if (op == '*') left * right else left / right
            }

            // Functions
            if (expr.startsWith("bh_pow(") && expr.endsWith(")")) {
                val inner = expr.substring(7, expr.length - 1)
                val comma = findTopLevelComma(inner)
                val b = evalNode(inner.substring(0, comma))
                val e = evalNode(inner.substring(comma + 1))
                return bhPow(b, e)
            }
            if (expr.startsWith("pow(") && expr.endsWith(")")) {
                val inner = expr.substring(4, expr.length - 1)
                val comma = findTopLevelComma(inner)
                val b = evalNode(inner.substring(0, comma))
                val e = evalNode(inner.substring(comma + 1))
                return b.pow(e)
            }
            if (expr.startsWith("max(") && expr.endsWith(")")) {
                val inner = expr.substring(4, expr.length - 1)
                val comma = findTopLevelComma(inner)
                val a = evalNode(inner.substring(0, comma))
                val b = evalNode(inner.substring(comma + 1))
                return max(a, b)
            }
            if (expr.startsWith("min(") && expr.endsWith(")")) {
                val inner = expr.substring(4, expr.length - 1)
                val comma = findTopLevelComma(inner)
                val a = evalNode(inner.substring(0, comma))
                val b = evalNode(inner.substring(comma + 1))
                return min(a, b)
            }
            if (expr.startsWith("abs(") && expr.endsWith(")")) {
                val inner = expr.substring(4, expr.length - 1)
                return evalNode(inner).absoluteValue
            }

            return expr.toDouble()
        }

        return evalNode(s)
    }

    private fun isMatchingOuterParens(str: String): Boolean {
        var depth = 0
        for (i in 0 until str.length - 1) {
            if (str[i] == '(') depth++
            else if (str[i] == ')') {
                depth--
                if (depth == 0) return false
            }
        }
        return true
    }

    private fun findTopLevelComma(str: String): Int {
        var depth = 0
        for (i in str.indices) {
            when (str[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun findLowestPrecedenceOp(str: String, ops: List<String>): Int {
        var depth = 0
        var foundIdx = -1
        for (i in str.indices.reversed()) {
            when (str[i]) {
                ')' -> depth++
                '(' -> depth--
                else -> {
                    if (depth == 0) {
                        for (op in ops) {
                            if (str.startsWith(op, i)) {
                                if (op == "-" && (i == 0 || str[i - 1] in "(+-*/," || str[i - 1].lowercaseChar() == 'e')) {
                                    // unary minus or exponent in scientific notation like 1e-6, skip
                                    continue
                                }
                                return i
                            }
                        }
                    }
                }
            }
        }
        return foundIdx
    }

    private fun findTernaryQuestion(str: String): Int {
        var depth = 0
        for (i in str.indices) {
            when (str[i]) {
                '(' -> depth++
                ')' -> depth--
                '?' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun findMatchingColon(str: String, qIndex: Int): Int {
        var depth = 0
        for (i in (qIndex + 1) until str.length) {
            when (str[i]) {
                '(' -> depth++
                ')' -> depth--
                ':' -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun evalCondition(str: String): Boolean {
        if ("<" in str) {
            val parts = str.split("<")
            return parts[0].toDouble() < parts[1].toDouble()
        }
        return false
    }

    @Test
    fun testPowerParityGrid() {
        val expressions = listOf(
            "(t - 5)^2",
            "t^3",
            "(t / 10)^2",
            "2^t",
            "t^0",
            "(t - 3)^-1",
            "t^0.5"
        )

        println("=== POWER OPERATOR PARITY GRID (-20.0..20.0, step 0.25) ===")
        for (expr in expressions) {
            val teq = TEquation(expr)
            val glsl = GlslTranspiler.transpile(expr)
            var maxDiff = 0.0
            var pointsTested = 0
            var nanPoints = 0

            var t = -20.0
            while (t <= 20.01) {
                // For (t - 3)^-1, skip singularity at t = 3.0
                if (expr == "(t - 3)^-1" && abs(t - 3.0) < 1e-4) {
                    t += 0.25
                    continue
                }

                val expected = teq.evaluate(t)
                val actual = evalGlsl(glsl, t)

                if (expected.isNaN()) {
                    nanPoints++
                    // For t < 0 on t^0.5, exp4j gives NaN, shader safely returns 0.0
                    assertEquals(0.0, actual, 1e-6, "Shader must safely yield 0.0 where exp4j gives NaN for '$expr' at t=$t")
                } else {
                    val diff = abs(expected - actual)
                    if (diff > maxDiff) maxDiff = diff
                    assertTrue(
                        diff < 1e-3,
                        "Equation '$expr' mismatch at t=$t: expected $expected (exp4j), but got $actual (GLSL: $glsl), diff=$diff"
                    )
                    pointsTested++
                }

                t += 0.25
            }
            println("Expression: %-15s | Max Diff: %.6f px | Valid Points: %3d | NaN Fallback Points: %2d".format(
                expr, maxDiff, pointsTested, nanPoints
            ))
            println("  -> Transpiled GLSL: $glsl")
        }
        println("=============================================================")
    }

    @Test
    fun testPowerGlslangValidator() {
        val expressions = listOf(
            "(t - 5)^2",
            "t^3",
            "2^t",
            "(t - 3)^-1",
            "abs(t)^0.5"
        )

        for (expr in expressions) {
            val glsl = GlslTranspiler.transpile(expr)
            val shaderSource = """
                #version 150
                in vec3 Position;
                out vec4 vertexColor;

                float bh_pow(float b, float e) {
                    if (b == 0.0) return 0.0;
                    if (floor(e) == e) {
                        if (floor(e * 0.5) * 2.0 == e) return pow(abs(b), e);
                        else return sign(b) * pow(abs(b), e);
                    }
                    return b >= 0.0 ? pow(b, e) : 0.0;
                }

                void main() {
                    float t = Position.x;
                    float y = $glsl;
                    gl_Position = vec4(t, y, Position.z, 1.0);
                    vertexColor = vec4(1.0);
                }
            """.trimIndent()

            val tempFile = File.createTempFile("power_lint_", ".vert")
            try {
                tempFile.writeText(shaderSource)
                val process = ProcessBuilder("glslangValidator", "-S", "vert", tempFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                assertEquals(0, exitCode, "glslangValidator failed for '$expr' (GLSL: $glsl):\n$output")
            } finally {
                tempFile.delete()
            }
        }
    }
}
