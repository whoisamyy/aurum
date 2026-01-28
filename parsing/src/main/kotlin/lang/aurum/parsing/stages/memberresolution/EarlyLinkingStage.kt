package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.Method
import lang.aurum.model.Type
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.stages.*

class EarlyLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        val linkTable = mutableMapOf<String, Type>()
        fileContext.importMap.typeMap.values.forEach { t -> InternalLinker.link(t as? MutableType,
            LinkingContext(fileContext, linkTable)
        ) }

        val removalQueue = mutableListOf<String>()

        fileContext.importMap.symbolMap.forEach { (alias, symbol) ->
            val split = symbol.split(".")

            var drop = 0
            var linked = InternalLinker.linkWithJvm(
                split.joinToString("."),
                LinkingContext(fileContext, linkTable)
            )
            while (linked == null && drop < split.size) {
                drop++
                linked = InternalLinker.linkWithJvm(
                    split.dropLast(drop).joinToString("."),
                    LinkingContext(fileContext, linkTable)
                )
            }

            if (linked == null)
                throw IllegalStateException("todo")

            if (drop == 0) {
                fileContext.importMap.typeMap += alias to linked
            } else if (drop == 1) {
                fileContext.importMap += alias to linked.getMethods(split.last())
                    .filter(Method::isStatic)
                    .toSet()

                linked.findField(split.last()).ifPresent {
                    if (!it.isStatic) throw IllegalStateException("todo")

                    fileContext.importMap += alias to it
                }
            } else {
                //  A
                //  l   drop == 0 == true

                // A . b
                // l  d1

                // 2   1   0
                // A . b . c
                // l  d2  d1
                linked.findField(split.reversed()[drop-1]).ifPresent {
                    if (!it.isStatic) throw IllegalStateException("todo")
                    removalQueue += alias
                    fileContext.importMap += it.name() to it
                    fileContext.importMap += alias to "${it.name()}.${split.subList(split.size-drop+1, split.size).joinToString(".")}"
                }
            }
        }
    }
}