package kr.toxicity.hud.animation

import java.util.concurrent.ConcurrentHashMap

data class AnimationElementInfo(
    val name: String,
    val mode: AnimationMode,
    val type: AnimationType,
    val reason: String
)

object AnimationElementRegistry {
    private val elements = ConcurrentHashMap<String, AnimationElementInfo>()

    fun register(name: String, mode: AnimationMode, type: AnimationType, reason: String) {
        elements[name] = AnimationElementInfo(name, mode, type, reason)
    }

    fun getAll(): Map<String, AnimationElementInfo> = elements

    fun clear() {
        elements.clear()
    }
}
