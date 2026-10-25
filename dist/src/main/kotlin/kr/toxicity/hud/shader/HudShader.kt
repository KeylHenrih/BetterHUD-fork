package kr.toxicity.hud.shader

import kr.toxicity.hud.animation.ClientAnimation
import kr.toxicity.hud.animation.ListenerAnimation
import kr.toxicity.hud.animation.SequenceAnimation
import kr.toxicity.hud.location.GuiLocation

data class HudShader(
    val gui: GuiLocation,
    val renderScale: RenderScale,
    val layer: Int,
    val outline: Int,
    val opacity: Double,
    val property: Int,
    val clientAnimation: ClientAnimation? = null,
    val listenerAnimation: ListenerAnimation? = null,
    val sequenceAnimation: SequenceAnimation? = null,
    val isText: Boolean = false,
    val isProbe: Boolean = false,
    val moduloM: Int = 240,
    val name: String = ""
) : Comparable<HudShader> {
    companion object {
        private val clientAnimationComparator: Comparator<ClientAnimation?> = Comparator.nullsFirst(Comparator.naturalOrder<ClientAnimation>())
        private val listenerAnimationComparator: Comparator<ListenerAnimation?> = Comparator.nullsFirst(Comparator.naturalOrder<ListenerAnimation>())
        private val sequenceAnimationComparator: Comparator<SequenceAnimation?> = Comparator.nullsFirst(Comparator.naturalOrder<SequenceAnimation>())
        private val comparator = Comparator.comparing { s: HudShader ->
            s.gui
        }.thenComparing { s: HudShader ->
            s.renderScale
        }.thenComparing { s: HudShader ->
            s.layer
        }.thenComparing { s: HudShader ->
            s.outline
        }.thenComparing { s: HudShader ->
            s.opacity
        }.thenComparing { s: HudShader ->
            s.property
        }.thenComparing({ s: HudShader -> s.clientAnimation }, clientAnimationComparator)
        .thenComparing({ s: HudShader -> s.listenerAnimation }, listenerAnimationComparator)
        .thenComparing({ s: HudShader -> s.sequenceAnimation }, sequenceAnimationComparator)
        .thenComparing { s: HudShader -> s.isText }
        .thenComparing { s: HudShader -> s.isProbe }
        .thenComparingInt { s: HudShader -> s.moduloM }
        .thenComparing { s: HudShader -> s.name }
    }

    override fun compareTo(other: HudShader): Int {
        return comparator.compare(this, other)
    }

    fun toBackground(otherOpacity: Double) = HudShader(
        gui,
        renderScale,
        layer,
        0,
        opacity * otherOpacity,
        property,
        clientAnimation,
        null,
        null,
        moduloM = moduloM
    )

    fun toFancyHead() = HudShader(
        gui,
        renderScale * 1.125,
        layer + 1,
        0x8F shl 24,
        opacity,
        property,
        clientAnimation,
        listenerAnimation,
        sequenceAnimation,
        moduloM = moduloM
    )
}
