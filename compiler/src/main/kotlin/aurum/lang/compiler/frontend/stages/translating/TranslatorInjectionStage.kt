package aurum.lang.compiler.frontend.stages.translating

import aurum.lang.compiler.backend.ir.IRTranslator
import aurum.lang.compiler.backend.jvm.ClassTranslator
import aurum.lang.compiler.backend.jvm.JVMTranslator
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.TargetArtifact
import aurum.lang.compiler.frontend.stages.TranslatorFactory

class TranslatorInjectionStage : Stage() {
    val target = input<TargetArtifact>()
    val factory = output<TranslatorFactory<*>>()

    override fun execute() {
        when (target.get().target) {
            "IR" -> factory.set(TranslatorFactory(::IRTranslator))
            "INTERPRET" -> factory.set(TranslatorFactory(::ClassTranslator))
            else -> factory.set(TranslatorFactory(::JVMTranslator))
        }
    }
}