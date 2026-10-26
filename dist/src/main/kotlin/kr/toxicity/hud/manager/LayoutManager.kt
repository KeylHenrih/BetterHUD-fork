package kr.toxicity.hud.manager

import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.layout.LayoutGroup
import kr.toxicity.hud.resource.GlobalResource
import kr.toxicity.hud.util.*
import java.io.File

object LayoutManager : BetterHudManager {

    override val managerName: String = "Layout"
    override val supportExternalPacks: Boolean = true

    private val layoutMap = HashMap<String, LayoutGroup>()

    override fun start() {

    }

    fun getLayout(name: String) = synchronized(layoutMap) {
        layoutMap[name]
    }

    override fun preReload() {
        layoutMap.clear()
    }

    override fun reload(workingDirectory: File, info: ReloadInfo, resource: GlobalResource) {
        workingDirectory.subFolder("layouts").forEachAllYaml(info.sender) { file, s, yamlObject ->
            val isDebugLayout = file.name.startsWith("debug_probe") || file.name.startsWith("debug_image") || file.name.startsWith("debug_color") || file.name.startsWith("debug-color") || file.name.startsWith("debug_italic") || file.name.startsWith("debug-italic")
            if (isDebugLayout && !ConfigManagerImpl.animationDebugProbe && !ConfigManagerImpl.debug()) {
                return@forEachAllYaml
            }
            runCatching {
                layoutMap.putSync("layout") {
                    LayoutGroup(s, info.sender, yamlObject)
                }
            }.handleFailure(info) {
                "Unable to load this layout: $s in ${file.name}"
            }
        }
        if (ConfigManagerImpl.animationDebugProbe || ConfigManagerImpl.debug()) {
            kr.toxicity.hud.debug.DebugProbeHelper.injectDebugItalicLayouts { name, yaml ->
                if (!layoutMap.containsKey(name)) {
                    layoutMap.putSync("layout") {
                        LayoutGroup(name, info.sender, yaml)
                    }
                }
            }
        }
    }

    override fun end() {
    }
}