package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.ir.*
import aurum.lang.model.attribute.PrimaryConstructorAttribute
import java.lang.reflect.AccessFlag

class PrimaryConstructorResolvingStage : Stage() {
    val types = input<ProcessedTypes>()

    init {
        dependsOn<TypeDefiningStage>()
    }

    override fun execute() {
        types.get().forEach {
            val constructor = it.type.methods()
                .find { m -> m.attributes().contains<PrimaryConstructorAttribute>() } as? MutableMethod
                ?: return@forEach

            val cp = it.file.constantPool
            val code = mutableListOf<Instruction>(
                InvokeConstructor(Reference.Super, listOf())
            )

            constructor.parameters().forEach { p ->
                if (p.attributes().contains<PrimaryConstructorAttribute>()) {
                    code += PutField(
                        Reference.This,
                        cp.getReference(it.type.withDefaultTypeArguments().findField(p.name()).orElseThrow()),
                        Reference.Named(p.name())
                    )
                }
            }

            code += Return()

            constructor.attributes += CodeAttribute(code)
            constructor.accessFlags += AccessFlag.PUBLIC
        }
    }
}