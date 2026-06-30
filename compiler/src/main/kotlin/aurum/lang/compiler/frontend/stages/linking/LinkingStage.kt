package aurum.lang.compiler.frontend.stages.linking

import aurum.lang.compiler.frontend.stages.Files
import aurum.lang.compiler.frontend.stages.LinkerFactory
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.analyzing.TypeDefiningStage

class LinkingStage : Stage() {
    val files = input<Files>()
    val linker = input<LinkerFactory<*>>()

    init {
        dependsOn<TypeDefiningStage>()
    }

    override fun execute() {
        files.get().forEach {
            linker.get()().link(it.imports)
        }
    }
}