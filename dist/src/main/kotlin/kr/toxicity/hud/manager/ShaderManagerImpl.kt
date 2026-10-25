package kr.toxicity.hud.manager

import kr.toxicity.hud.animation.EasingType
import kr.toxicity.hud.api.manager.ShaderManager
import kr.toxicity.hud.api.manager.ShaderManager.*
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.configuration.PluginConfiguration
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.pack.PackOverlay
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.shader.HudShader
import kr.toxicity.hud.util.*
import net.kyori.adventure.bossbar.BossBar
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object ShaderManagerImpl : BetterHudManager, ShaderManager {

    override val managerName: String = "Shader"
    override val supportExternalPacks: Boolean = false

    var barColor = BossBar.Color.YELLOW
        private set

    private val tagPattern = Pattern.compile("#(?<name>[a-zA-Z]+)")
    internal val tagBuilders: MutableMap<String, (ShaderType) -> List<String>> = mutableMapOf(
        "CreateConstant" to { _ ->
            constants.map {
                "#define ${it.key} ${it.value}"
            }
        },
        "CreateLayout" to { _ ->
            compiledLayout
        },
        "GenerateOtherDefinedMethod" to { type ->
            when (type) {
                ShaderType.TEXT_VERTEX -> {
                    val slowmo = ConfigManagerImpl.animationDebugSlowmo
                    val slowmoStr = if (slowmo != 1.0) " * ${slowmo.toFloat()}" else ""
                    val base = listOf(
                        "out vec2 hudFill;",
                        "out vec2 hudQuadUV;",
                        "float jmod(float x, float y) { float m = x - y * floor(x / y); return m >= y ? m - y : (m < 0.0 ? m + y : m); }",
                        "float getElapsedTicks(float tStart, float gameTimeTicks) {",
                        "    return (jmod(gameTimeTicks - tStart + 10.0, 240.0) - 10.0)$slowmoStr;",
                        "}",
                        "float getElapsedTicks(float tStart, float gameTimeTicks, float M) {",
                        "    return (jmod(gameTimeTicks - tStart + 10.0, M) - 10.0)$slowmoStr;",
                        "}",
                        "float getElapsedTicks(float tStart, float gameTimeTicks, float M, float L) {",
                        "    return (jmod(gameTimeTicks - tStart + L, M) - L)$slowmoStr;",
                        "}",
                        "float easeLinear(float x) { return x; }",
                        "float easeOutCubic(float x) { float f = x - 1.0; return f * f * f + 1.0; }",
                        "float easeInOutCubic(float x) { if (x < 0.5) return 4.0 * x * x * x; float f = 2.0 * x - 2.0; return (x - 1.0) * f * f + 1.0; }",
                        "float easeOutQuad(float x) { return x * (2.0 - x); }",
                        "float easeInOutQuad(float x) { if (x < 0.5) return 2.0 * x * x; return -1.0 + (4.0 - 2.0 * x) * x; }",
                        "float easeOutSine(float x) { return sin((x * 3.1415926535) / 2.0); }",
                        "float easeInOutSine(float x) { return -(cos(3.1415926535 * x) - 1.0) / 2.0; }"
                    )
                    base
                }
                ShaderType.TEXT_FRAGMENT -> listOf(
                    "in vec2 hudFill;",
                    "in vec2 hudQuadUV;",
                    "bool shouldDiscard(vec2 uv, float fill, int splitType) {",
                    "    if (fill <= 0.0) return true;",
                    "    if (fill >= 1.0) return false;",
                    "    if (splitType == 0) return uv.x > fill;",
                    "    if (splitType == 1) return uv.x < (1.0 - fill);",
                    "    if (splitType == 2) return uv.y < (1.0 - fill);",
                    "    if (splitType == 3) return uv.y > fill;",
                    "    float PI = 3.1415926535;",
                    "    float d = -(atan(uv.y - 0.5, uv.x - 0.5) + PI / 2.0);",
                    "    if (splitType == 5) d = -d;",
                    "    if (d < 0.0) d += 2.0 * PI;",
                    "    if (d > 2.0 * PI) d -= 2.0 * PI;",
                    "    return d > (fill * 2.0 * PI);",
                    "}"
                )
            }
        },
        "GenerateOtherStartMethod" to { type ->
            when (type) {
                ShaderType.TEXT_VERTEX -> listOf(
                    "hudFill = vec2(-1.0, -1.0);",
                    "int hudCorner = int(gl_VertexID) & 3;",
                    "hudQuadUV = vec2(float((hudCorner == 2 || hudCorner == 3) ? 1.0 : 0.0), float((hudCorner == 1 || hudCorner == 2) ? 1.0 : 0.0));"
                )
                ShaderType.TEXT_FRAGMENT -> emptyList()
            }
        },
        "GenerateOtherMainMethod" to { type ->
            when (type) {
                ShaderType.TEXT_VERTEX -> emptyList()
                ShaderType.TEXT_FRAGMENT -> listOf(
                    "if (hudFill.x >= 0.0) {",
                    "    if (shouldDiscard(hudQuadUV, hudFill.x, int(hudFill.y + 0.5))) {",
                    "        discard;",
                    "    }",
                    "}"
                )
            }
        }
    )

    private val hudShaders = TreeMap<HudShader, MutableList<(Int) -> Unit>>()
    private var compiledLayout = arrayListOf<String>()

    private val tagSupplierMap = ConcurrentHashMap<ShaderType, ShaderTagSupplier>()


    @Synchronized
    fun addHudShader(shader: HudShader, consumer: (Int) -> Unit) {
        synchronized(hudShaders) {
            hudShaders.computeIfAbsent(shader) {
                ArrayList()
            }.add(consumer)
        }
    }

    override fun addTagSupplier(type: ShaderType, supplier: ShaderTagSupplier) {
        tagSupplierMap[type] = tagSupplierMap[type]?.let {
            it + supplier
        } ?: supplier
    }

    private val constants = mutableMapOf<String, String>()
    private var replaceSet = mutableSetOf<String>()

    private val shaderConstants = mutableMapOf(
        "HEIGHT_BIT" to HUD_DEFAULT_BIT.toString(),
        "MAX_BIT" to HUD_MAX_BIT.toString(),
        "ADD_OFFSET" to HUD_ADD_HEIGHT.toString()
    )

    override fun addConstant(key: String, value: String) {
        shaderConstants[key] = value
    }

    private fun backupAndClearShaders(shadersDir: File) {
        if (!shadersDir.exists()) return
        val parent = shadersDir.parentFile ?: return
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val backupDir = File(parent, "shaders.bak-$timestamp")
        val renamed = shadersDir.renameTo(backupDir)
        if (renamed) {
            info("Backed up existing shaders/ to ${backupDir.name}")
        } else {
            shadersDir.deleteRecursively()
        }
        parent.listFiles()?.filter { it.isDirectory && it.name.startsWith("shaders.bak-") }
            ?.sortedByDescending { it.name }
            ?.drop(3)
            ?.forEach { it.deleteRecursively() }
    }

    override fun start() {
        if (ConfigManagerImpl.deleteShadersOnStart) {
            File(DATA_FOLDER, "shaders").let { shadersDir ->
                backupAndClearShaders(shadersDir)
            }
        }
        ShaderType.entries.forEach {
            addTagSupplier(it, EMPTY_SUPPLIER)
        }
    }

    override fun preReload() {
        if (ConfigManagerImpl.deleteShadersOnStart) {
            File(DATA_FOLDER, "shaders").let { shadersDir ->
                backupAndClearShaders(shadersDir)
            }
        }
    }

    override fun reload(workingDirectory: File, info: ReloadInfo, resource: GlobalResource) {
        constants.clear()
        replaceSet = mutableSetOf()
        constants += shaderConstants
        constants["DEFAULT_OFFSET"] = "${10 + 17 * (ConfigManagerImpl.bossbarResourcePackLine - 1)}"
        runCatching {
            val yaml = PluginConfiguration.SHADER.create()
            barColor = yaml["bar-color"]?.asString()?.let {
                runCatching {
                    BossBar.Color.valueOf(it.uppercase())
                }.getOrNull()
            } ?: BossBar.Color.RED
            fun copy(suffix: String) {
                BOOTSTRAP.resource("background.png")?.buffered()?.use { input ->
                    val byte = input.readAllBytes()
                    PackGenerator.addTask(resource.bossBar + listOf("sprites", "boss_bar", "${barColor.name.lowercase()}_$suffix.png")) {
                        byte
                    }
                }
            }
            copy("background")
            copy("progress")
            BOOTSTRAP.resource("bars.png")?.buffered()?.use { target ->
                val oldImage = target.toImage()
                val yAxis = 10 * barColor.ordinal
                PackGenerator.addTask(resource.bossBar + "bars.png") {
                    BufferedImage(oldImage.width, oldImage.height, BufferedImage.TYPE_INT_ARGB).apply {
                        createGraphics().run {
                            if (barColor.ordinal > 0) drawImage(
                                oldImage.getSubimage(
                                    0,
                                    0,
                                    oldImage.width,
                                    yAxis
                                ), 0, 0, null
                            )
                            drawImage(
                                oldImage.getSubimage(
                                    0,
                                    yAxis + 10,
                                    oldImage.width,
                                    oldImage.height - yAxis - 10
                                ), 0, yAxis + 10, null
                            )
                            dispose()
                        }
                    }.toByteArray()
                }
            }
            if (yaml.getAsBoolean("disable-level-text", false)) replaceSet += "HideExp"
            compileShader(resource)
        }.handleFailure(info) {
            "Unable to load shader.yml"
        }
    }

    fun generateShaderLayout(shaders: Map<HudShader, List<(Int) -> Unit>>): List<String> {
        val maxLayouts = (1 shl HUD_MAX_BIT) - 1
        if (shaders.size > maxLayouts) {
            val overflownNames = shaders.keys.drop(maxLayouts).map { it.name.ifEmpty { "shader#${it.hashCode()}" } }.distinct()
            runCatching {
                warn("Layout slot overflow: BetterHUD supports up to $maxLayouts unique shader layouts (current count: ${shaders.size}). The following layouts fell back to static rendering: $overflownNames")
            }
        }
        val arr = arrayListOf<String>()
        arr.add("case 0: {")
        arr.add("    // Reserved safe static fallback case for overflown layouts")
        arr.add("    break;")
        arr.add("}")
        val result = shaders.entries.foldIndexed(arr) { index, list, entry ->
            val shader = entry.key
            val id = index + 1
            if (id > maxLayouts) {
                entry.value.forEach { it(0) }
                return@foldIndexed list
            }
            list.add("case ${id}: {")
            if (shader.property > 0) arr.add("    property = ${shader.property};")
            if (shader.opacity < 1.0) arr.add("    opacity = ${shader.opacity.toFloat()};")
            val isProbe = shader.isProbe || (shader.isText && ConfigManagerImpl.animationDebugProbe)
            if (isProbe) {
                arr.addAll(kr.toxicity.hud.debug.DebugProbeHelper.generateProbeShaderCase())
            } else {
                val anim = shader.clientAnimation
                val listenerAnim = shader.listenerAnimation
                val hasPlayOnce = anim?.type == kr.toxicity.hud.animation.AnimationType.PLAY_ONCE

                if (anim != null) {
                    if (hasPlayOnce) {
                        if (shader.isText) {
                            val M = shader.moduloM
                            arr.add("    int actualA = int(round(Color.a * 255.0));")
                            arr.add("    int actualR = int(round(Color.r * 255.0));")
                            arr.add("    int actualG = int(round(Color.g * 255.0));")
                            arr.add("    int actualB = int(round(Color.b * 255.0));")
                            if (M <= 120) {
                                arr.add("    if (actualA >= 1 && actualA <= $M) {")
                                arr.add("        float tStart = float(actualA - 1);")
                                arr.add("        float elapsed = getElapsedTicks(tStart, GameTime * 24000.0, ${M}.0);")
                                arr.add("        if (elapsed < 0.0) {")
                                arr.add("            pos.xy = vec2(-10000.0);")
                                arr.add("            vertexColor = vec4(0.0);")
                                arr.add("            opacity = 0.0;")
                                arr.add("        } else {")
                                arr.add("            float t = clamp(elapsed, 0.0, ${anim.duration}.0);")
                                arr.add("            pos.xy -= vec2(1.0, 1.0);")
                                if (anim.xGlsl != "0.0" && anim.xGlsl != "0") {
                                    arr.add("            pos.x += (${anim.xGlsl});")
                                }
                                if (anim.yGlsl != "0.0" && anim.yGlsl != "0") {
                                    arr.add("            pos.y += (${anim.yGlsl});")
                                }
                                if (anim.opacityGlsl != "1.0" && anim.opacityGlsl != "1") {
                                    arr.add("            opacity *= clamp(${anim.opacityGlsl}, 0.0, 1.0);")
                                }
                                arr.add("            vertexColor = vec4(float(actualR) / 255.0, float(actualG) / 255.0, float(actualB) / 255.0, 1.0);")
                                arr.add("        }")
                                arr.add("    } else if (actualA >= ${M + 1} && actualA <= ${2 * M}) {")
                                arr.add("        float tStart = float(actualA - 1 - $M);")
                                arr.add("        float elapsed = getElapsedTicks(tStart, GameTime * 24000.0, ${M}.0);")
                                arr.add("        if (elapsed < 0.0) {")
                                arr.add("            pos.xy = vec2(-10000.0);")
                                arr.add("            vertexColor = vec4(0.0);")
                                arr.add("            opacity = 0.0;")
                                arr.add("        } else {")
                                arr.add("            float t = clamp(elapsed, 0.0, ${anim.duration}.0);")
                                if (anim.xGlsl != "0.0" && anim.xGlsl != "0") {
                                    arr.add("            pos.x += (${anim.xGlsl});")
                                }
                                if (anim.yGlsl != "0.0" && anim.yGlsl != "0") {
                                    arr.add("            pos.y += (${anim.yGlsl});")
                                }
                                if (anim.opacityGlsl != "1.0" && anim.opacityGlsl != "1") {
                                    arr.add("            opacity *= clamp(${anim.opacityGlsl}, 0.0, 1.0);")
                                }
                                arr.add("            vertexColor = vec4(float(actualR) / 255.0, float(actualG) / 255.0, float(actualB) / 255.0, 1.0);")
                                arr.add("        }")
                                arr.add("    } else {")
                                arr.add("        pos.xy = vec2(-10000.0);")
                                arr.add("        vertexColor = vec4(0.0);")
                                arr.add("        opacity = 0.0;")
                                arr.add("    }")
                            } else {
                                arr.add("    if (actualA >= 1 && actualA <= 240) {")
                                arr.add("        float tStart = float(actualA - 1);")
                                arr.add("        float elapsed = getElapsedTicks(tStart, GameTime * 24000.0);")
                                arr.add("        if (elapsed < 0.0) {")
                                arr.add("            pos.xy = vec2(-10000.0);")
                                arr.add("            vertexColor = vec4(0.0);")
                                arr.add("            opacity = 0.0;")
                                arr.add("        } else {")
                                arr.add("            float t = clamp(elapsed, 0.0, ${anim.duration}.0);")
                                arr.add("            pos.xy -= vec2(1.0, 1.0);")
                                if (anim.xGlsl != "0.0" && anim.xGlsl != "0") {
                                    arr.add("            pos.x += (${anim.xGlsl});")
                                }
                                if (anim.yGlsl != "0.0" && anim.yGlsl != "0") {
                                    arr.add("            pos.y += (${anim.yGlsl});")
                                }
                                if (anim.opacityGlsl != "1.0" && anim.opacityGlsl != "1") {
                                    arr.add("            opacity *= clamp(${anim.opacityGlsl}, 0.0, 1.0);")
                                }
                                arr.add("            vertexColor = vec4(float(actualR) / 255.0, float(actualG) / 255.0, float(actualB) / 255.0, 1.0);")
                                arr.add("        }")
                                arr.add("    } else {")
                                arr.add("        pos.xy = vec2(-10000.0);")
                                arr.add("        vertexColor = vec4(0.0);")
                                arr.add("        opacity = 0.0;")
                                arr.add("    }")
                            }
                        } else {
                            val M = shader.moduloM
                            val elapsedCall = if (M == 240) "getElapsedTicks(tStart, GameTime * 24000.0)" else "getElapsedTicks(tStart, GameTime * 24000.0, ${M}.0)"
                            arr.add("    float tStart = floor(Color.r * 255.0 + 0.5);")
                            arr.add("    float elapsed = $elapsedCall;")
                            arr.add("    if (tStart < ${M}.0) {")
                            arr.add("        if (elapsed < 0.0) {")
                            arr.add("            pos.xy = vec2(-10000.0);")
                            arr.add("            vertexColor = vec4(0.0);")
                            arr.add("            opacity = 0.0;")
                            arr.add("        } else {")
                            arr.add("            float t = clamp(elapsed, 0.0, ${anim.duration}.0);")
                            if (anim.xGlsl != "0.0" && anim.xGlsl != "0") {
                                arr.add("            pos.x += (${anim.xGlsl});")
                            }
                            if (anim.yGlsl != "0.0" && anim.yGlsl != "0") {
                                arr.add("            pos.y += (${anim.yGlsl});")
                            }
                            if (anim.opacityGlsl != "1.0" && anim.opacityGlsl != "1") {
                                arr.add("            opacity *= clamp(${anim.opacityGlsl}, 0.0, 1.0);")
                            }
                            arr.add("            vertexColor = vec4(1.0, 1.0, 1.0, 1.0);")
                            arr.add("        }")
                            arr.add("    }")
                        }
                    } else {
                        arr.add("    float t = jmod(GameTime * 24000.0, ${anim.duration}.0);")
                        if (anim.xGlsl != "0.0" && anim.xGlsl != "0") {
                            arr.add("    pos.x += (${anim.xGlsl});")
                        }
                        if (anim.yGlsl != "0.0" && anim.yGlsl != "0") {
                            arr.add("    pos.y += (${anim.yGlsl});")
                        }
                        if (anim.opacityGlsl != "1.0" && anim.opacityGlsl != "1") {
                            arr.add("    opacity *= clamp(${anim.opacityGlsl}, 0.0, 1.0);")
                        }
                    }
                }
                if (listenerAnim != null) {
                    val dur = listenerAnim.duration
                    val easeFunc = listenerAnim.easing.glslFunction
                    if (!hasPlayOnce) {
                        val elapsedCall = if (shader.moduloM == 240) "getElapsedTicks(tStart, GameTime * 24000.0)" else "getElapsedTicks(tStart, GameTime * 24000.0, ${shader.moduloM}.0)"
                        arr.add("    float tStart = floor(Color.r * 255.0 + 0.5);")
                        arr.add("    float elapsed = $elapsedCall;")
                    }
                    arr.add("    float fromVal = Color.g;")
                    arr.add("    float toVal = Color.b;")
                    if (dur > 0) {
                        arr.add("    if (abs(fromVal - toVal) < 0.001) {")
                        arr.add("        hudFill = vec2(clamp(toVal, 0.0, 1.0), ${listenerAnim.splitType.ordinal}.0);")
                        arr.add("    } else {")
                        arr.add("        float progress = clamp(elapsed / ${dur}.0, 0.0, 1.0);")
                        arr.add("        float fill = clamp(fromVal + (toVal - fromVal) * $easeFunc(progress), 0.0, 1.0);")
                        arr.add("        hudFill = vec2(fill, ${listenerAnim.splitType.ordinal}.0);")
                        arr.add("    }")
                    } else {
                        arr.add("    hudFill = vec2(clamp(toVal, 0.0, 1.0), ${listenerAnim.splitType.ordinal}.0);")
                    }
                    arr.add("    vertexColor = vec4(1.0, 1.0, 1.0, 1.0);")
                }
                val seq = shader.sequenceAnimation
                if (seq != null) {
                    arr.add("    int activeFrame = int(jmod(GameTime * 24000.0, ${seq.totalFrames}.0));")
                    arr.add("    if (${seq.frameIndex} != activeFrame) {")
                    arr.add("        pos = vec3(-10000.0, -10000.0, -10000.0);")
                    arr.add("        vertexColor = vec4(0.0);")
                    arr.add("    }")
                }
            }
            val static = shader.renderScale.scale.staticScale
            fun applyScale(offset: Int, scale: Double, pos: String) {
                if (scale != 1.0 || static) {
                    val scaleFloat = scale.toFloat()
                    arr.add("    pos.$pos = (pos.$pos - (${offset})) * ${if (static) "$scaleFloat * uiScreen.$pos" else scaleFloat} + (${offset});")
                }
            }
            applyScale(shader.renderScale.relativeOffset.x, shader.renderScale.scale.x, "x")
            applyScale(shader.renderScale.relativeOffset.y, shader.renderScale.scale.y, "y")
            if (shader.gui.x != 0.0) arr.add("    xGui = ui.x * ${shader.gui.x.toFloat()} / 100.0;")
            if (shader.gui.y != 0.0) arr.add("    yGui = ui.y * ${shader.gui.y.toFloat()} / 100.0;")
            if (shader.layer != 0) arr.add("    layer = ${shader.layer};")
            arr.add("    break;")
            arr.add("}")
            entry.value.forEach {
                it(id)
            }
            arr
        }
        result.add("default: {")
        result.add("    break;")
        result.add("}")
        return result
    }

    private fun compileShader(resource: GlobalResource) {
        compiledLayout = ArrayList(generateShaderLayout(hudShaders))
        for (overlay in PackOverlay.entries) {
            loadShaders(overlay).forEach { (key, byte) ->
                val path = resource.core + key
                PackGenerator.addTask(listOf(overlay.overlayName) + path) {
                    byte
                }
            }
        }
        compiledLayout = arrayListOf()
        hudShaders.clear()
    }

    private fun loadShaders(overlay: PackOverlay): List<Pair<String, ByteArray>> {
        val shaders = ShaderType.entries.map {
            it to it.lines(overlay.overlayName)
        }
        return shaders.map { (key, args) ->
            val tagSupplier = (tagSupplierMap[key] ?: EMPTY_SUPPLIER).get()
            val bytes = buildString {
                args.forEach write@ { string ->
                    var s = string
                    if (s.startsWith("//")) {
                        val get = s.substringBefore(' ')
                        if (replaceSet.contains(get.substring(2))) s = s.substring(get.length)
                    }
                    if (s.isEmpty()) return@write
                    val tagMatcher = tagPattern.matcher(s)
                    if (tagMatcher.find()) {
                        val group = tagMatcher.group("name")
                        val built = tagBuilders[group]?.invoke(key)
                        val supplied = tagSupplier[group]
                        val list = if (built != null && supplied != null) built + supplied else built ?: supplied
                        list?.let {
                            it.forEach apply@ { methodString ->
                                val trimmed = methodString.trim()
                                if (trimmed.isEmpty() || trimmed.startsWith("//")) return@apply
                                val appendEnter = trimmed.first() == '#'
                                if (appendEnter && (isEmpty() || last() != '\n')) append('\n')
                                append(trimmed)
                                if (appendEnter) append('\n')
                            }
                            return@write
                        }
                    }
                    var tr = s.trim()
                    if (tr.isNotEmpty()) {
                        if (isNotEmpty() && tr.first() == '#') {
                            if (last() != '\n') tr = '\n' + tr
                            tr += '\n'
                        }
                        append(tr.substringBeforeLast("//"))
                    }
                }
            }.toByteArray()
            "${overlay.shaderCoreName}.${key.extension}" to bytes
        }
    }

    override fun end() {
    }
}
