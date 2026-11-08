package kr.toxicity.hud.equation

import net.objecthunter.exp4j.function.Function
import net.objecthunter.exp4j.shuntingyard.ShuntingYard
import net.objecthunter.exp4j.tokenizer.*
import java.util.*

object GlslTranspiler {
    private val builtInFunctions = arrayOf(
        object : Function("min", 2) {
            override fun apply(vararg p0: Double): Double = 0.0
        },
        object : Function("max", 2) {
            override fun apply(vararg p0: Double): Double = 0.0
        },
        object : Function("clamp", 3) {
            override fun apply(vararg p0: Double): Double = 0.0
        }
    ).associateBy { it.name }

    private val supportedVariables = setOf("t", "pi", "e")

    fun transpile(expression: String): String {
        val trimmed = expression.trim()
        if (trimmed == "0" || trimmed == "0.0") return "0.0"
        if (trimmed == "1" || trimmed == "1.0") return "1.0"

        val tokens = ShuntingYard.convertToRPN(
            trimmed,
            builtInFunctions,
            emptyMap(),
            supportedVariables,
            true
        )

        val stack = ArrayDeque<String>()

        for (token in tokens) {
            when (token) {
                is NumberToken -> {
                    val value = token.value
                    if (value == value.toLong().toDouble() && !value.isInfinite() && !value.isNaN()) {
                        stack.push("${value.toLong()}.0")
                    } else {
                        stack.push(String.format(Locale.US, "%.6f", value))
                    }
                }
                is VariableToken -> {
                    when (token.name) {
                        "t" -> stack.push("t")
                        "pi" -> stack.push("3.14159265")
                        "e" -> stack.push("2.71828182")
                        else -> throw IllegalArgumentException("Unsupported variable in GLSL: ${token.name}")
                    }
                }
                is OperatorToken -> {
                    val op = token.operator
                    when (op.numOperands) {
                        1 -> {
                            val a = stack.pop()
                            when (op.symbol) {
                                "-" -> stack.push("(-($a))")
                                "+" -> stack.push(a)
                                else -> throw IllegalArgumentException("Unsupported unary operator in GLSL: ${op.symbol}")
                            }
                        }
                        2 -> {
                            val right = stack.pop()
                            val left = stack.pop()
                            when (op.symbol) {
                                "+" -> stack.push("($left + $right)")
                                "-" -> stack.push("($left - $right)")
                                "*" -> stack.push("($left * $right)")
                                "/" -> stack.push("($left / (abs($right) < 1e-6 ? 1e-6 : ($right)))")
                                "%" -> stack.push("($left - $right * trunc($left / (abs($right) < 1e-6 ? 1e-6 : ($right))))")
                                "^" -> {
                                    val expInt = parseConstantIntegerExponent(right)
                                    if (expInt != null && expInt in -8..8) {
                                        when {
                                            expInt == 0 -> stack.push("1.0")
                                            expInt == 1 -> stack.push("($left)")
                                            expInt in 2..8 -> {
                                                val factors = List(expInt) { "($left)" }.joinToString(" * ")
                                                stack.push("($factors)")
                                            }
                                            expInt == -1 -> {
                                                stack.push("(1.0 / (abs($left) < 1e-6 ? 1e-6 : ($left)))")
                                            }
                                            else -> {
                                                val posExp = -expInt
                                                val factors = List(posExp) { "($left)" }.joinToString(" * ")
                                                stack.push("(1.0 / (abs($factors) < 1e-6 ? 1e-6 : ($factors)))")
                                            }
                                        }
                                    } else {
                                        stack.push("bh_pow($left, $right)")
                                    }
                                }
                                else -> throw IllegalArgumentException("Unsupported binary operator in GLSL: ${op.symbol}")
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported operator operand count: ${op.numOperands}")
                    }
                }
                is FunctionToken -> {
                    val fn = token.function
                    val argCount = fn.numArguments
                    val args = ArrayList<String>(argCount)
                    for (i in 0 until argCount) {
                        args.add(stack.pop())
                    }
                    args.reverse()

                    val glslFunc = when (val name = fn.name.lowercase()) {
                        "sin", "cos", "tan", "asin", "acos", "atan", "abs", "sqrt", "ceil", "floor", "exp", "min", "max", "clamp" -> {
                            "$name(${args.joinToString(", ")})"
                        }
                        "log" -> "log(${args[0]})"
                        "log10" -> "(log(${args[0]}) / 2.30258509)"
                        "log2" -> "log2(${args[0]})"
                        "cbrt" -> "(sign(${args[0]}) * pow(abs(${args[0]}), 0.33333333))"
                        "sinh" -> "(0.5 * (exp(${args[0]}) - exp(-(${args[0]}))))"
                        "cosh" -> "(0.5 * (exp(${args[0]}) + exp(-(${args[0]}))))"
                        "tanh" -> "((exp(2.0 * (${args[0]})) - 1.0) / (exp(2.0 * (${args[0]})) + 1.0))"
                        "signum" -> "sign(${args[0]})"
                        else -> throw IllegalArgumentException("Unsupported function in GLSL: $name")
                    }
                    stack.push(glslFunc)
                }
                else -> throw IllegalArgumentException("Unknown token type: ${token.javaClass.simpleName}")
            }
        }

        if (stack.size != 1) {
            throw IllegalStateException("Invalid expression, stack size after evaluation: ${stack.size}")
        }

        return stack.pop()
    }

    private fun parseConstantIntegerExponent(str: String): Int? {
        var s = str.trim()
        while (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length - 1).trim()
        }
        var isNegative = false
        if (s.startsWith("-")) {
            isNegative = true
            s = s.substring(1).trim()
            while (s.startsWith("(") && s.endsWith(")")) {
                s = s.substring(1, s.length - 1).trim()
            }
        }
        val d = s.toDoubleOrNull() ?: return null
        if (d == kotlin.math.floor(d) && !d.isInfinite() && !d.isNaN()) {
            val i = if (isNegative) -d.toInt() else d.toInt()
            if (i in -8..8) return i
        }
        return null
    }
}
