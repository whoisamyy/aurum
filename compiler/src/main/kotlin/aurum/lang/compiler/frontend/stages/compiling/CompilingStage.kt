package aurum.lang.compiler.frontend.stages.compiling

import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.backend.ir.IRCompiler
import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.analyzing.CodeBlockAttribute
import aurum.lang.compiler.frontend.stages.analyzing.DefaultParametersProcessingStage

class CompilingStage : Stage() {
    val types = input<ProcessedTypes>()

//    val newTypes = output<ProcessedTypes>()

    init {
        dependsOn<DefaultParametersProcessingStage>()
    }

    override fun execute() {
        types.get().forEach { t ->
            val cp = t.file.constantPool
            (t.type as? MutableType)?.attributes?.add(ConstantPoolAttribute(cp))
            t.type.methods()
                .filterIsInstance<MutableMethod>()
                .filter { it.attributes.contains<CodeBlockAttribute>() }
                .forEach {
                    val codeBlock = it.attributes.get<CodeBlockAttribute>()!!
                    val compiler = IRCompiler(it, codeBlock.typeResolver, cp)
                    it.attributes += compiler.compile(codeBlock.codeBlock)
                }
        }

//        newTypes.set(ProcessedTypes(newProcessedTypes))
    }
}