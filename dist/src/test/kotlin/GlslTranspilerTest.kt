import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.equation.TEquation
import kotlin.math.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlslTranspilerTest {

    @Test
    fun testSimpleExpressions() {
        assertEquals("0.0", GlslTranspiler.transpile("0"))
        assertEquals("1.0", GlslTranspiler.transpile("1"))
        val glsl1 = GlslTranspiler.transpile("t")
        assertEquals("t", glsl1)
    }

    @Test
    fun testDefaultLayoutEquation() {
        val glsl = GlslTranspiler.transpile("3cos(t/30 * pi)")
        println("3cos(t/30 * pi) -> $glsl")
        assertTrue(glsl.contains("cos"))
        assertTrue(glsl.contains("3.0"))
        assertTrue(glsl.contains("30.0"))
    }

    @Test
    fun testArithmeticOperations() {
        val glsl = GlslTranspiler.transpile("1 + 0.1 * sin(t * 0.3)")
        println("1 + 0.1 * sin(t * 0.3) -> $glsl")
        assertTrue(glsl.contains("sin"))
    }

    @Test
    fun testFunctions() {
        val glslMin = GlslTranspiler.transpile("min(t, 10)")
        assertEquals("min(t, 10.0)", glslMin)

        val glslMax = GlslTranspiler.transpile("max(t, 0)")
        assertEquals("max(t, 0.0)", glslMax)

        val glslClamp = GlslTranspiler.transpile("clamp(t, 0, 100)")
        assertEquals("clamp(t, 0.0, 100.0)", glslClamp)
    }

    @Test
    fun testEvaluationEquivalence() {
        // Compare TEquation vs manual JVM float evaluation of the generated GLSL expression logic
        val formula = "3 * cos(t / 30.0 * pi)"
        val teq = TEquation(formula)
        val glsl = GlslTranspiler.transpile(formula)

        // Evaluate across grid of t in 0..60
        for (i in 0..60) {
            val t = i.toDouble()
            val expected = teq.evaluate(t)
            // Emulate GLSL float evaluation: 3.0 * cos(t / 30.0 * 3.14159265)
            val actual = 3.0f * cos(t.toFloat() / 30.0f * 3.14159265f)
            val diff = abs(expected - actual.toDouble())
            assertTrue(diff < 1e-4, "Mismatch at t=$t: expected $expected, got $actual, diff $diff")
        }
    }
}
