package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.stages.AurumFile
import aurum.lang.compiler.frontend.stages.Files
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.parsing.ParsingStage
import aurum.lang.model.Member
import aurum.lang.model.Type

class ImportProcessingStage : Stage() {
    val files = input<Files>()
    val outFiles = output<Files>()

    init {
        dependsOn<ParsingStage>()
    }

    override fun execute() {
        outFiles.set(Files(files.get().files.map(::processFile)))
    }

    private fun processFile(file: AurumFile): AurumFile {
        val imports = mutableListOf<ASTNode.Import>()

        file.ast
            .filterIsInstance<ASTNode.Import>()
            .forEach {
                imports += it
            }

        file.imports = ImportMap()
        file.imports.putAll(imports.associate {
            (it.alias ?: it.import.identifiers.last()) to it.import
        })

        return file
    }
}


class ImportMap : HashMap<String, ASTNode.QualifiedName>(), MutableMap<String, ASTNode.QualifiedName> {
    lateinit var types: MutableMap<String, Type>

    // maps strings to list because multiple methods can have same (full)name
    lateinit var members: MutableMap<String, List<Member>>
}