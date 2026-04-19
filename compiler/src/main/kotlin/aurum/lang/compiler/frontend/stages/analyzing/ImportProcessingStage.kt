@file:Suppress("JavaDefaultMethodsNotOverriddenByDelegation")

package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.stages.AurumFile
import aurum.lang.compiler.frontend.stages.Files
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.parsing.ParsingStage

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

        file.imports = imports.associate {
            (it.alias ?: it.import.identifiers.last()) to it.import
        } as ImportMap

        return file
    }
}


typealias ImportMap = MutableMap<String, ASTNode.QualifiedName>