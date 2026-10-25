package kr.toxicity.hud.animation

import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.equation.EquationTriple
import kr.toxicity.hud.equation.GlslTranspiler
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.util.warn

data class AnimationLocation(
    val type: AnimationType,
    val location: List<PixelLocation>,
    val clientAnimation: ClientAnimation? = null
) {
    companion object {
        val zero = AnimationLocation(AnimationType.LOOP, listOf(PixelLocation.zero), null)

        private fun parse(
            section: YamlObject,
            type: AnimationType,
            duration: Int,
            parentPath: String = ""
        ): AnimationLocation {
            val rawPath = (section as? kr.toxicity.hud.api.yaml.YamlConfiguration)?.path()?.takeIf { it.isNotEmpty() } ?: "animation"
            val elemName = if (parentPath.isNotEmpty()) "$parentPath/animation" else rawPath
            val modeStr = section["mode"]?.asString()?.lowercase()
            val globalIsClient = ConfigManagerImpl.animationMode == AnimationMode.CLIENT || ConfigManagerImpl.animationMode == AnimationMode.HYBRID
            val isClient = when (modeStr) {
                "client" -> true
                "server" -> false
                else -> globalIsClient
            }
            if (!isClient) {
                val reason = if (modeStr == "server") "Explicit mode: server in YAML" else "Global animation-mode is SERVER"
                AnimationElementRegistry.register(elemName, AnimationMode.SERVER, type, reason)
                return AnimationLocation(type, duration, EquationTriple(section))
            }
            if (type != AnimationType.LOOP && type != AnimationType.PLAY_ONCE) {
                val reason = "Unsupported animation type '$type' for client mode (only LOOP and PLAY_ONCE supported)"
                warn("Animation element '$elemName' fell back to SERVER mode: $reason")
                AnimationElementRegistry.register(elemName, AnimationMode.SERVER, type, reason)
                return AnimationLocation(type, duration, EquationTriple(section))
            }
            val xRaw = section["x-equation"]?.asString()
            val yRaw = section["y-equation"]?.asString()
            if (xRaw == null || yRaw == null) {
                val reason = "Missing x-equation or y-equation"
                warn("Animation element '$elemName' fell back to SERVER mode: $reason")
                AnimationElementRegistry.register(elemName, AnimationMode.SERVER, type, reason)
                return AnimationLocation(type, duration, EquationTriple(section))
            }
            var transpileErr: String? = null
            val clientAnim = runCatching {
                val xGlsl = GlslTranspiler.transpile(xRaw)
                val yGlsl = GlslTranspiler.transpile(yRaw)
                val opGlsl = section["opacity-equation"]?.asString()?.let {
                    GlslTranspiler.transpile(it)
                } ?: "1.0"
                ClientAnimation(duration, xGlsl, yGlsl, opGlsl, type)
            }.onFailure {
                transpileErr = it.message ?: "Unknown error"
                warn("Animation element '$elemName' fell back to SERVER mode: GLSL transpilation failed: $transpileErr")
            }.getOrNull()

            return if (clientAnim != null) {
                AnimationElementRegistry.register(elemName, AnimationMode.CLIENT, type, "Transpiled to GLSL shader (${type.name})")
                AnimationLocation(type, listOf(PixelLocation.zero), clientAnim)
            } else {
                val reason = "GLSL transpilation failed: $transpileErr"
                AnimationElementRegistry.register(elemName, AnimationMode.SERVER, type, reason)
                AnimationLocation(type, duration, EquationTriple(section))
            }
        }
    }

    constructor(
        type: AnimationType,
        duration: Int,
        imageEquation: EquationTriple
    ): this(
        type,
        (0..<duration).map {
            val d = it.toDouble()
            PixelLocation(
                imageEquation.x evaluateToInt d,
                imageEquation.y evaluateToInt d,
                imageEquation.opacity evaluate d
            )
        },
        null
    )

    constructor(section: YamlObject, parentPath: String = ""): this(
        section,
        section["type"]?.asString()?.let {
            runCatching {
                AnimationType.valueOf(it.uppercase())
            }.getOrNull()
        } ?: AnimationType.LOOP,
        section.getAsInt("duration", 20).coerceAtLeast(1),
        parentPath
    )

    private constructor(
        section: YamlObject,
        type: AnimationType,
        duration: Int,
        parentPath: String,
        parsed: AnimationLocation = parse(section, type, duration, parentPath)
    ): this(
        parsed.type,
        parsed.location,
        parsed.clientAnimation
    )
}
