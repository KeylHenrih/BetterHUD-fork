package kr.toxicity.hud.popup

import com.google.gson.JsonArray
import kr.toxicity.hud.api.configuration.HudObjectType
import kr.toxicity.hud.api.player.HudPlayer
import kr.toxicity.hud.api.popup.Popup
import kr.toxicity.hud.api.popup.Popup.FrameType
import kr.toxicity.hud.api.popup.PopupSortType
import kr.toxicity.hud.api.popup.PopupUpdater
import kr.toxicity.hud.api.update.UpdateEvent
import kr.toxicity.hud.api.yaml.YamlObject
import kr.toxicity.hud.configuration.HudConfiguration
import kr.toxicity.hud.equation.EquationPairLocation
import kr.toxicity.hud.location.PixelLocation
import kr.toxicity.hud.manager.*
import kr.toxicity.hud.pack.PackGenerator
import kr.toxicity.hud.location.GuiLocation
import kr.toxicity.hud.placeholder.PlaceholderSource
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.util.*
import java.util.*

class PopupImpl(
    override val id: String,
    resource: GlobalResource,
    section: YamlObject
) : Popup, HudConfiguration, PlaceholderSource by PlaceholderSource.Impl(section) {
    val gui = GuiLocation(section)
    val move = section["move"]?.asObject()?.let {
        EquationPairLocation(it)
    } ?: EquationPairLocation.zero
    private val duration = section.getAsInt("duration", -1)
    private val group = section["group"]?.asString() ?: id
    private val unique = section.getAsBoolean("unique", false)
    private val queue = duration > 0 && section.getAsBoolean("queue", false)
    private val push = !queue && section.getAsBoolean("push", false)
    private val alwaysCheckCondition = queue && section.getAsBoolean("always-check-condition", true)
    private val default = ConfigManagerImpl.defaultPopup.contains(id) || section.getAsBoolean("default", false)
    private val keyMapping = section.getAsBoolean("key-mapping", false)
    private val index: ((UpdateEvent) -> (HudPlayer) -> Int)? = section["index"]?.asString()?.let {
        PlaceholderManagerImpl.find(it, this).assertNumber {
            "this index is not a number. it is ${clazz.simpleName}."
        }.let { placeholderBuilder ->
            { reason ->
                (placeholderBuilder build reason).let { placeholder ->
                    { player ->
                        (placeholder(player) as Number).toInt()
                    }
                }
            }
        }
    }
    private val tick = section.getAsLong("tick", 1)
    private val frameType = section.getAsString("frame-type", "local").uppercase().run {
        FrameType.valueOf(this)
    }

    private val imageEncoded = "popup_${name}_image".encodeKey(EncodeManager.EncodeNamespace.FONT)
    var array: JsonArray? = JsonArray()
    val imageKey = createAdventureKey(imageEncoded)

    private val spaces = intKeyMapOf<String>()
    private var imageChar = 0xCE000

    fun getOrCreateSpace(int: Int): String = spaces.computeIfAbsent(int) {
        newChar
    }

    val newChar
        get() = (++imageChar).parseChar()

    private val sortType = section["sort"]?.asString()?.let {
        PopupSortType.valueOf(it.uppercase())
    } ?: PopupSortType.LAST

    private val layoutPair = section["layouts"]?.asObject()?.let {
        val json = array.ifNull { "error is occurred." }
        var i = 0
        val sLayouts = ArrayList<PopupLayout>()
        val cLayouts = ArrayList<PopupLayout>()
        it.forEachSubConfiguration { _, yamlObject ->
            val layout = yamlObject["name"]?.asString().ifNull { "name value not set." }
            var loc = GuiLocation(yamlObject)
            yamlObject["gui"]?.asObject()?.let { gui ->
                loc += GuiLocation(gui)
            }
            val layoutObj = LayoutManager.getLayout(layout).ifNull { "this layout doesn't exist: $layout" }
            val anim = layoutObj.animation
            val clientAnim = anim.clientAnimation
            val fullPath = "popup_${this@PopupImpl.name}/$layout/animation"
            val hasHead = layoutObj.head.isNotEmpty()
            val pixel = yamlObject["pixel"]?.asObject()?.let { PixelLocation(it) } ?: PixelLocation.zero

            val maxDuration = maxOf(clientAnim?.duration ?: 0, if (this@PopupImpl.duration > 0) this@PopupImpl.duration else 0)
            val lookahead = ConfigManagerImpl.animationLookaheadMax
            val exceedsWindow = clientAnim != null && (maxDuration + lookahead > (240 - lookahead))
            val hasText = layoutObj.text.isNotEmpty()
            var layoutM = 240
            var enableClient = clientAnim != null && !hasHead && !exceedsWindow

            if (hasHead && clientAnim != null) {
                val reason = "head not supported in client mode (fallback to server)"
                warn("Popup '${this@PopupImpl.name}' layout '$layout' has heads, forcing SERVER mode.")
                kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.SERVER, anim.type, reason)
            } else if (exceedsWindow) {
                val reason = "duration ($maxDuration) exceeds safe time window (fallback to server)"
                warn("Popup '${this@PopupImpl.name}' layout '$layout' duration exceeds safe window, forcing SERVER mode.")
                kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.SERVER, anim.type, reason)
            } else if (enableClient && clientAnim != null) {
                if (clientAnim.type == kr.toxicity.hud.animation.AnimationType.PLAY_ONCE) {
                    val requiredTicks = maxDuration + maxOf(lookahead, 10) + 4
                    val divisorM = kr.toxicity.hud.text.getDivisorM(requiredTicks)
                    if (ConfigManagerImpl.textShadowMode == ConfigManagerImpl.TextShadowMode.DUAL && hasText) {
                        if (divisorM != null) {
                            layoutM = divisorM
                            kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.CLIENT, anim.type, "Transpiled to GLSL shader (exact dual mode, M=$layoutM)")
                        } else {
                            if (ConfigManagerImpl.textShadowFallback == ConfigManagerImpl.TextShadowFallback.SERVER) {
                                enableClient = false
                                warn("Popup '${this@PopupImpl.name}' layout '$layout' duration ($maxDuration) exceeds max shadow window M=120, forcing SERVER mode.")
                                kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.SERVER, anim.type, "duration ($maxDuration) exceeds max shadow window M=120 (fallback to server)")
                            } else {
                                layoutM = 240
                                kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.CLIENT, anim.type, "Transpiled to GLSL shader (exact single-copy, shadow fallback)")
                            }
                        }
                    } else {
                        layoutM = divisorM ?: 240
                        kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.CLIENT, anim.type, "Transpiled to GLSL shader (exact single-copy, M=$layoutM)")
                    }
                } else {
                    kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.CLIENT, anim.type, "Transpiled to GLSL shader (loop)")
                }
            } else {
                kr.toxicity.hud.animation.AnimationElementRegistry.register(fullPath, kr.toxicity.hud.animation.AnimationMode.SERVER, anim.type, "Server fallback or legacy layout")
            }

            val serverLayout = PopupLayout(
                ++i,
                json,
                layoutObj,
                this@PopupImpl,
                loc,
                pixel,
                resource.font,
                enableClientAnimation = false,
                layoutM = 240
            )
            sLayouts.add(serverLayout)
            if (enableClient) {
                val clientLayout = PopupLayout(
                    i,
                    json,
                    layoutObj,
                    this@PopupImpl,
                    loc,
                    pixel,
                    resource.font,
                    enableClientAnimation = true,
                    layoutM = layoutM
                )
                cLayouts.add(clientLayout)
            } else {
                cLayouts.add(serverLayout)
            }
        }
        sLayouts to cLayouts
    }.ifNull { "layouts configuration not set." }

    private val serverLayouts = layoutPair.first.ifEmpty {
        throw RuntimeException("layouts is empty.")
    }
    private val clientLayouts = layoutPair.second.ifEmpty {
        throw RuntimeException("layouts is empty.")
    }

    private val conditions = section.toConditions(this)

    init {
        val task = { event: UpdateEvent, uuid: UUID ->
            PlayerManagerImpl.getHudPlayer(uuid)?.let { player ->
                show(event, player)
            }
        }
        section["triggers"]?.asObject()?.forEachSubConfiguration { _, yamlObject ->
            TriggerManagerImpl.getTrigger(yamlObject).registerEvent { t, u ->
                task(u, t)
            }
        }
        section["hide-triggers"]?.asObject()?.forEachSubConfiguration { _, yamlObject ->
            TriggerManagerImpl.getTrigger(yamlObject).registerEvent { t, u ->
                PlayerManagerImpl.getHudPlayer(t)?.let {
                    hide(it)
                } == true
                task(u, t)
            }
        }
        array?.let { arr ->
            if (spaces.isNotEmpty()) arr.add(jsonObjectOf(
                "type" to "space",
                "advances" to jsonObjectOf(*spaces.map {
                    it.value to it.key
                }.toTypedArray())
            ))
            PackGenerator.addTask(resource.font + "$imageEncoded.json") {
                jsonObjectOf("providers" to arr).toByteArray()
            }
        }
        array = null
    }

    override fun getType(): HudObjectType<*> = HudObjectType.POPUP
    override fun tick(): Long = tick
    override fun frameType(): FrameType = frameType

    override fun getMaxStack(): Int = move.locations.size
    override fun show(reason: UpdateEvent, player: HudPlayer): PopupUpdater? {
        if (keyMapping) {
            player.popupKeyMap[reason.key]?.let {
                if (it.update()) return null
                else player.popupKeyMap.remove(reason.key)
            }
        }
        return show0(reason, player)?.apply {
            if (keyMapping) {
                player.popupKeyMap[reason.key] = this
            }
        }
    }
    private fun show0(reason: UpdateEvent, player: HudPlayer): PopupUpdater? {
        val key = reason.key
        val get = player.popupGroupIteratorMap.computeIfAbsent(group) {
            PopupIteratorGroupImpl()
        }
        val buildCondition = conditions build reason
        if (!buildCondition(player)) return null
        var updater = {
        }
        var cond = {
            buildCondition(player)
        }
        if (duration > 0) {
            val old = cond
            var i = 0
            val old2 = updater
            updater = {
                i = 0
                old2()
            }
            cond = {
                ++i < duration && old()
            }
        }
        val value = index?.invoke(reason)?.invoke(player) ?: -1
        val remove0 = {
            player.popupKeyMap.remove(key)
            Unit
        }
        val impl = player as? kr.toxicity.hud.player.HudPlayerImpl
        val isClient = (impl?.isClientAnimationEnabled() ?: (ConfigManagerImpl.animationMode != kr.toxicity.hud.animation.AnimationMode.SERVER)) &&
                ConfigManagerImpl.textClientMode != ConfigManagerImpl.TextClientMode.OFF
        val targetLayouts = if (isClient) clientLayouts else serverLayouts
        val iterator = PopupIteratorImpl(
            reason,
            player,
            targetLayouts,
            this,
            unique,
            lastIndex,
            key,
            sortType,
            id,
            queue,
            push,
            alwaysCheckCondition,
            value,
            cond,
            remove0
        )
        get.addIterator(iterator)
        return object : PopupUpdater {
            override fun update(): Boolean {
                if (iterator.markedAsRemoval()) return false
                updater()
                return true
            }
            override fun remove() {
                iterator.remove()
                if (get.index == 0) player.popupGroupIteratorMap.remove(group)
            }
            override fun getIndex(): Int = iterator.index
            override fun setIndex(index: Int) {
                iterator.priority = index.coerceAtMost(iterator.maxIndex)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PopupImpl

        return id == other.id
    }

    override fun getName(): String = id
    override fun getGroupName(): String = group
    override fun hashCode(): Int = id.hashCode()
    override fun isDefault(): Boolean = default
}