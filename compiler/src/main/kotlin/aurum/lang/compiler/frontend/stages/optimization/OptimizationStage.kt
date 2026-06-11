package aurum.lang.compiler.frontend.stages.optimization

import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.stages.CompilationData
import aurum.lang.compiler.frontend.stages.ProcessedTypes
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.compiling.CompilingStage
import aurum.lang.ir.CodeAttribute
import aurum.lang.ir.ConstantPool
import aurum.lang.ir.Instruction

// todo:
// implement cfg-level optimizations

class OptimizationStage : Stage() {
    val types = input<ProcessedTypes>()
    val compilationData = input<CompilationData>()

    init {
        dependsOn<CompilingStage>()
    }

    override fun execute() {
        val level = compilationData.get().optimizationLevel
        val passes = PASSES.filter { level >= it.minOptLevel }
        if (passes.isEmpty()) return

        types.get().forEach { processed ->
            val poolAttribute = processed.type.attributes().get<ConstantPoolAttribute>() ?: return@forEach
            val constantPool = poolAttribute.constantPool

            processed.type.methods().forEach { method ->
                val code = method.attributes().get<CodeAttribute>()?.code ?: return@forEach
                optimize(constantPool, code, level, passes)
            }
        }
    }

    companion object {
        private const val MAX_ITERATIONS = 8

        private val PASSES: List<Optimizer> = listOf(
            CopyPropagation,
//            ConstantFolding,
            GroupRefInlining,
            DeadCodeElimination,
        )

        internal fun optimize(
            constantPool: ConstantPool,
            instructions: MutableList<Instruction>,
            level: Int,
            passes: List<Optimizer> = PASSES.filter { level >= it.minOptLevel },
        ) {
            var iteration = 0
            var changed: Boolean
            do {
                changed = false
                for (pass in passes) {
                    val context = OptimizationContext(constantPool, instructions, level)
                    if (pass.optimize(context)) {
                        changed = true
                    }
                }
                iteration++
            } while (changed && iteration < MAX_ITERATIONS)
        }
    }
}
