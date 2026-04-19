package aurum.lang.compiler.frontend

import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.analyzing.TypeProcessingStage
import aurum.lang.model.Type

class OutputStage : Stage() {
    val types = input<ProcessedTypes>()

    init {
        dependsOn<TypeProcessingStage>()
    }

    override fun execute() {
        types.get().forEach {
            printType(it.type)
        }
    }

    private fun printType(type: Type) {
        println(type.toUsageString())
        println("\tMethods:")
        println(type.methods().joinToString("\n\t\t", prefix = "\t\t"))
        println("\tFields:")
        println(type.fields().joinToString("\n\t\t", prefix = "\t\t") { "${it.owner().toUsageString()}.${it.name()}: ${it.type().toUsageString()}" })
    }
}