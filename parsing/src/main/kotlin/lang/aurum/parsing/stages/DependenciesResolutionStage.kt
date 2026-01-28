package lang.aurum.parsing.stages

import lang.aurum.model.Field
import lang.aurum.model.Method
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.model.MutableTypePool

class DependenciesResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        val parsingCtx = parsingContext
        val fileFinder = FileFinder(fileContext, parsingCtx.workDir, parsingCtx.classPath)
        fileContext.ctx.importStmt()?.forEach {
            parsingCtx.files += fileFinder.findFiles(
                it.qualifiedName().Identifier().map { id -> id.text }.toList()
            ).map { p -> FileContext.ofPath(parsingCtx, p) }

            val id = it.Identifier()
            if (id != null) {
                fileContext.importMap += id.text to it.qualifiedName().text
            } else {
                fileContext.importMap += Symbol(
                    it.qualifiedName().Identifier().dropLast(1).joinToString(".") { id -> id.text },
                    it.qualifiedName().Identifier().last().text
                )
            }
        }
    }

    override fun afterFileContext(fileContext: FileContext) {
        HashMap(fileContext.importMap.typeMap).entries.forEach { (alias, it) ->
            if (it !is MutableType) return@forEach
            var fullName = it.fullName()
            val memberPath = mutableListOf<String>()
            var type: MutableType = it
            while (fullName.isNotEmpty() && !InternalLinker.linkWithJvm(
                    fullName,
                    type,
                    LinkingContext(fileContext, HashMap(fileContext.importMap.typeMap))
            )) {
                val split = fullName.split(".")
                memberPath.addFirst(split.last())
                type = MutableTypePool.get(
                    fullName.split(".").last(),
                    fullName.split(".").dropLast(1).joinToString("."),
                    typeArguments = type.typeArguments,
                    typeParameters = type.typeParameters
                )
                fullName = split.dropLast(1).joinToString(".")
            }

            if (memberPath.size == 1) {
                fileContext.importMap.typeMap.remove(alias)

                type.members().filter { m -> m.name() == memberPath[0] }
                    .forEach { m ->
                        when (m) {
                            is Method -> fileContext.importMap += alias to m
                            is Field -> fileContext.importMap += alias to m
                        }
                    }
            }
        }
    }
}