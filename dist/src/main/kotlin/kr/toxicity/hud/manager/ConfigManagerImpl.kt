package kr.toxicity.hud.manager

import kr.toxicity.hud.animation.AnimationMode
import kr.toxicity.hud.api.manager.ConfigManager
import kr.toxicity.hud.api.manager.ConfigManager.DebugLevel
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.api.version.MinecraftVersion
import kr.toxicity.hud.configuration.PluginConfiguration
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.pack.PackType
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.resource.KeyResource
import kr.toxicity.hud.util.*
import java.io.File
import java.text.DecimalFormat

object ConfigManagerImpl : BetterHudManager, ConfigManager {

    override val managerName: String = "Config"
    override val supportExternalPacks: Boolean = false

    var key = KeyResource(NAME_SPACE)
        private set
    private var line = 1

    var autoSaveTime = 300L * 20
        private set
    var locationProvideTime = 20L * 20
        private set

    var bossbarResourcePackLine = line
        private set

    var defaultHud = emptyList<String>()
        private set
    var defaultPopup = emptyList<String>()
        private set
    var defaultCompass = emptyList<String>()
        private set
    var versionCheck = true
        private set

    var numberFormat = DecimalFormat("#,###.#")
        private set
    var defaultFontName = "font.ttf"
        private set
    var tickSpeed = 1L
        private set
    var disableToBedrockPlayer = true
        private set
    var buildFolderLocation = "BetterHud/build".replace('/', File.separatorChar)
        private set
    var enableProtection = true
        private set
    var forceUpdate = false
        private set

    var mergeBossBar = true
        private set
    var packType = PackType.ZIP
        private set
    var enableSelfHost = false
        private set
    var selfHostIp = "*"
        private set
    var selfHostPort = 8163
        private set
    var mergeOtherFolders = emptyList<String>()
        private set

    var loadingHead = "random"
        private set
    private var debug = false
    private var debugLevel = DebugLevel.ASSETS

    var resourcePackObfuscation = false
        private set

    var clearBuildFolder = true
        private set

    var minecraftJarVersion: MinecraftVersion? = null

    var loadMinecraftDefaultTextures = true
        private set
    var includedMinecraftTextures = listOf(
        "block",
        "item"
    )
        private set

    var useLegacyFormat = false
    var legacySerializer = LEGACY_AMPERSAND
        private set
    private var removeDefaultHotbar = false
    var disableLegacyOffset = false
        private set

    val namespace
        get() = key.namespace

    var mergeWithExternalResources = false
        private set

    var animationMode = AnimationMode.SERVER
        private set
    var animationLookaheadMax = 5
        private set
    var animationDebugSlowmo = 1.0
        private set
    var animationDebugProbe = false
        private set

    enum class TextClientMode {
        EXACT,
        OFF
    }

    enum class TextShadowMode {
        DUAL,
        OFF
    }

    enum class TextShadowFallback {
        EXACT,
        SERVER
    }

    var textClientMode = TextClientMode.EXACT
        private set
    var textShadowMode = TextShadowMode.DUAL
        private set
    var textShadowFallback = TextShadowFallback.EXACT
        private set
    var deleteShadersOnStart = true
        private set

    override fun start() {
        preReload()
    }

    override fun reload(workingDirectory: File, info: ReloadInfo, resource: GlobalResource) {
        if (removeDefaultHotbar) {
            PLUGIN.loadAssets("empty") { n, i ->
                val read = i.readAllBytes()
                PackGenerator.addTask(n.split('/')) {
                    read
                }
            }
        }
    }

    override fun preReload() {
        runCatching {
            File(DATA_FOLDER, "version.txt").bufferedWriter().use {
                it.write(BOOTSTRAP.version())
            }
            val yaml = PluginConfiguration.CONFIG.create()
            debug = yaml.getAsBoolean("debug", false)
            val shaderYaml = runCatching { PluginConfiguration.SHADER.create() }.getOrNull()
            deleteShadersOnStart = shaderYaml?.get("delete-shaders-on-start")?.asBoolean() ?: yaml.getAsBoolean("delete-shaders-on-start", true)
            autoSaveTime = yaml.getAsLong("auto-save-time", 300) * 20
            locationProvideTime = yaml.getAsLong("location-provide-time", 1) * 20
            debugLevel = runCatching {
                DebugLevel.valueOf(yaml.getAsString("debug-level", "asset").uppercase())
            }.getOrElse {
                DebugLevel.ASSETS
            }
            defaultHud = yaml["default-hud"]?.asArray()?.map {
                it.asString()
            } ?: emptyList()
            defaultPopup = yaml["default-popup"]?.asArray()?.map {
                it.asString()
            } ?: emptyList()
            defaultCompass = yaml["default-compass"]?.asArray()?.map {
                it.asString()
            } ?: emptyList()
            yaml["default-font-name"]?.asString()?.let {
                defaultFontName = it
            }
            yaml["pack-type"]?.asString()?.let {
                runCatching {
                    packType = PackType.valueOf(it.uppercase())
                }.handleFailure {
                    "Unable to find this pack type: $it"
                }
            }
            tickSpeed = yaml.getAsLong("tick-speed", 1)
            numberFormat = yaml["number-format"]?.asString()?.let {
                runCatching {
                    DecimalFormat(it)
                }.handleFailure {
                    "Unable to read this number-format: $it"
                }.getOrNull()
            } ?: DecimalFormat("#,###.#")
            disableToBedrockPlayer = yaml.getAsBoolean("disable-to-bedrock-player", true)
            yaml["build-folder-location"]?.asString()?.let {
                buildFolderLocation = it.replace('/', File.separatorChar)
            }
            line = yaml.getAsInt("bossbar-line", 1).coerceAtLeast(1).coerceAtMost(7)
            var newLine = yaml.getAsInt("bossbar-resource-pack-line", 0)
            if (newLine < 1) {
                newLine = line
            }
            if (bossbarResourcePackLine != newLine) {
                bossbarResourcePackLine = newLine
            }
            versionCheck = yaml.getAsBoolean("version-check", false)
            enableProtection = yaml.getAsBoolean("enable-protection", false)
            mergeBossBar = yaml.getAsBoolean("merge-boss-bar", true)
            enableSelfHost = yaml.getAsBoolean("enable-self-host", false)
            mergeOtherFolders = yaml["merge-other-folders"]?.asArray()?.map {
                it.asString()
            } ?: emptyList()
            yaml["self-host-ip"]?.asString()?.let { ip ->
                selfHostIp = ip
            }
            selfHostPort = yaml.getAsInt("self-host-port", 8163)
            forceUpdate = yaml.getAsBoolean("force-update", false)
            resourcePackObfuscation = yaml.getAsBoolean("resourcepack-obfuscation", false)
            if (yaml.getAsBoolean("metrics", false)) {
                BOOTSTRAP.startMetrics()
            } else {
                BOOTSTRAP.endMetrics()
            }
            yaml["loading-head"]?.asString()?.let {
                loadingHead = it
            }
            clearBuildFolder = yaml.getAsBoolean("clear-build-folder", true)
            loadMinecraftDefaultTextures = yaml.getAsBoolean("load-minecraft-default-textures", true)
            includedMinecraftTextures = yaml["included-minecraft-list"]?.asArray()?.map {
                it.asString()
            } ?: emptyList()
            useLegacyFormat = yaml.getAsBoolean("use-legacy-format",  false)
            yaml["legacy-serializer"]?.asString()?.let {
                runCatching {
                    legacySerializer = it.toLegacySerializer()
                }.handleFailure {
                    "Unable to find this legacy serializer: $it"
                }
            }
            key = KeyResource(yaml["namespace"]?.asString() ?: NAME_SPACE)
            minecraftJarVersion = yaml["minecraft-jar-version"]?.asString()?.let {
                runCatching {
                    it.toMinecraftVersion()
                }.getOrNull()
            }
            removeDefaultHotbar = yaml.getAsBoolean("remove-default-hotbar", false)
            disableLegacyOffset = yaml.getAsBoolean("disable-legacy-offset", false)
            mergeWithExternalResources = yaml.getAsBoolean("merge-with-external-resources", true)

            val shaderKeys = listOf(
                "animation-mode", "text-client-mode", "text-shadow-mode",
                "text-shadow-fallback", "animation-lookahead-max", "animation-debug-slowmo",
                "animation-debug-probe", "delete-shaders-on-start"
            )
            for (sKey in shaderKeys) {
                if (yaml[sKey] != null) {
                    if (shaderYaml?.get(sKey) != null) {
                        warn("Shader setting '$sKey' was found in config.yml, but shader.yml takes precedence. Please configure it in shader.yml.")
                    } else {
                        warn("Shader setting '$sKey' in config.yml is deprecated and will be read from config.yml; please move it to shader.yml.")
                    }
                }
            }

            fun getShaderSetting(key: String) = shaderYaml?.get(key) ?: yaml[key]

            val rawLookahead = getShaderSetting("animation-lookahead-max")?.asInt()
            if (rawLookahead != null && (rawLookahead < 0 || rawLookahead > 20)) {
                warn("animation-lookahead-max value $rawLookahead is out of range [0, 20]; clamping to ${rawLookahead.coerceIn(0, 20)}")
            }
            animationLookaheadMax = (rawLookahead ?: 5).coerceIn(0, 20)

            val rawSlowmo = getShaderSetting("animation-debug-slowmo")?.asDouble()
            if (rawSlowmo != null && (rawSlowmo < 0.01 || rawSlowmo > 10.0)) {
                warn("animation-debug-slowmo value $rawSlowmo is out of range [0.01, 10.0]; clamping to ${rawSlowmo.coerceIn(0.01, 10.0)}")
            }
            animationDebugSlowmo = (rawSlowmo ?: 1.0).coerceIn(0.01, 10.0)

            animationDebugProbe = getShaderSetting("animation-debug-probe")?.asBoolean() ?: false
            deleteShadersOnStart = getShaderSetting("delete-shaders-on-start")?.asBoolean() ?: true
            textClientMode = getShaderSetting("text-client-mode")?.asString()?.let { str ->
                runCatching {
                    TextClientMode.valueOf(str.uppercase())
                }.getOrElse {
                    warn("Unable to read text-client-mode '$str'; falling back to EXACT")
                    TextClientMode.EXACT
                }
            } ?: TextClientMode.EXACT

            textShadowMode = getShaderSetting("text-shadow-mode")?.asString()?.let { str ->
                runCatching {
                    TextShadowMode.valueOf(str.uppercase())
                }.getOrElse {
                    warn("Unable to read text-shadow-mode '$str'; falling back to DUAL")
                    TextShadowMode.DUAL
                }
            } ?: TextShadowMode.DUAL

            textShadowFallback = getShaderSetting("text-shadow-fallback")?.asString()?.let { str ->
                runCatching {
                    TextShadowFallback.valueOf(str.uppercase())
                }.getOrElse {
                    warn("Unable to read text-shadow-fallback '$str'; falling back to EXACT")
                    TextShadowFallback.EXACT
                }
            } ?: TextShadowFallback.EXACT

            animationMode = getShaderSetting("animation-mode")?.asString()?.let { str ->
                runCatching {
                    AnimationMode.valueOf(str.uppercase())
                }.getOrElse {
                    warn("Unable to read animation-mode '$str'; falling back to SERVER")
                    AnimationMode.SERVER
                }
            } ?: AnimationMode.SERVER
        }.handleFailure {
            "Unable to load config.yml"
        }
    }
    override fun end() {
    }

    override fun getBossbarLine(): Int = line
    override fun getDebugLevel(): DebugLevel = debugLevel
    override fun debug(): Boolean = debug
}