package aurum.lang.compiler.frontend.stages.linking

import aurum.lang.compiler.backend.jvm.JVMLinker
import aurum.lang.compiler.frontend.stages.LinkerFactory
import aurum.lang.compiler.frontend.stages.Stage

class LinkerInjectionStage : Stage() {
    val linker = output<LinkerFactory<*>>()

    override fun execute() {
        linker.set(LinkerFactory(::JVMLinker))
    }
}