package aurum.lang.compiler.frontend.stages.translating

import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.model.MutableTypePool
import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.analyzing.GeneratedClassAttribute
import aurum.lang.compiler.frontend.stages.optimization.OptimizationStage
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Type
import kotlin.io.path.Path

class TranslationStage : Stage() {
    val types = input<ProcessedTypes>()
    val translatorFactory = input<TranslatorFactory<*>>()

    val result = output<TranslationResults>()

    init {
        dependsOn<OptimizationStage>()
    }

    override fun execute() {
        val outTypes = mutableSetOf<Type>()
        outTypes += MutableTypePool.pool
            .filter { (k, type) ->
                val (_, typeArgs) = k

                typeArgs.isNullOrEmpty() && type.attributes.contains<GeneratedClassAttribute>()
            }
            .values

        val newProcessedTypes = mutableSetOf<ProcessedType>()
        newProcessedTypes += types.get()

        newProcessedTypes += outTypes.map {
            ProcessedType(
                it,
                ASTNode.DecoratorDeclaration(null, null, it.fullName()),
                AurumFile(
                    Path(it.pkg().replace('.', '/')).resolve(it.className()),
                    ""
                ).also { file ->
                    file.pkg = it.pkg()
                }
            )
        }

        val translators = newProcessedTypes.map {
            translatorFactory.get()(it).init()
        }
        result.set(TranslationResults(translators.map { TranslationResult(it.processedType, it.translate()) }))
    }
}