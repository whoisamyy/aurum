package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Type

class TypeProcessingStage : Stage() {
    val definedTypes = input<DefinedTypes>()
    val definedPackages = input<DefinedPackages>()

    val processedTypes = output<ProcessedTypes>()

    val types by lazy { definedTypes.get() }

    override fun execute() {
        definedPackages.get()

        val processed = types.groupBy { it.file }
            .flatMap { (file, definitions) -> processFile(file, definitions) }
            .toSet()

        processedTypes.set(ProcessedTypes(processed))
    }

    fun processFile(file: AurumFile, definitions: List<TypeDefinition>): List<ProcessedType> {
        val availableTypes = definitions.map(TypeDefinition::type) +
                file.importedTypes()

        val processor = TypeProcessor(availableTypes.toSet())
        return definitions
            .mapNotNull {
                if (it.type !is MutableType) {
                    print("WARN: trying to process non-mutable type ${it.type}. skipped")
                    return@mapNotNull null // for now just warn and continue loop
                }
                ProcessedType(processor.processType(it.type, it.declaration), it.declaration, file)
            }
    }

    private fun AurumFile.importedTypes(): List<Type> = types.map(TypeDefinition::type)
        .filter { it.fullName() in this.imports.values.map(ASTNode.QualifiedName::toString) }
}