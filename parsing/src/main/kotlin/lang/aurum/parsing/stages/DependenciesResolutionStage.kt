package lang.aurum.parsing.stages

import lang.aurum.parsing.model.MutableTypePool

class DependenciesResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        parsingContext.files.forEach {
            process(it)
        }
    }

    private fun process(fileContext: FileContext) {
        val parsingCtx = fileContext.parsingContext
        val fileFinder = FileFinder(fileContext, parsingCtx.workDir, parsingCtx.classPath)
        fileContext.ctx.importStmt()?.forEach {
            parsingCtx.files += fileFinder.findFiles(
                it.qualifiedName().Identifier().map { id -> id.text }.toList()
            ).map { p -> FileContext.ofPath(parsingCtx, p) }

            val id = it.Identifier()
            if (id != null) {
                fileContext.typeImportMap[id.text] = MutableTypePool.get(
                    it.qualifiedName().Identifier().last().text,
                    it.qualifiedName().Identifier().dropLast(1).joinToString(".") { id -> id.text }
                )
            } else {
                fileContext.typeImportMap[it.qualifiedName().Identifier().last().text] = MutableTypePool.get(
                    it.qualifiedName().Identifier().last().text,
                    it.qualifiedName().Identifier().dropLast(1).joinToString(".") { id -> id.text }
                )
            }
        }
    }
}