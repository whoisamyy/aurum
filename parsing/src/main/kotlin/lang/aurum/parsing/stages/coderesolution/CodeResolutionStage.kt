package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.CodeAttribute
import lang.aurum.model.Method
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.get
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableTypePool
import lang.aurum.parsing.stages.*

class CodeResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    lateinit var fileContext: FileContext

    override fun execute(fileContext: FileContext) {
        this.fileContext = fileContext
    }

    override fun execute(method: Method) {
        if (method !is MutableMethod) return
        method.attributes.get(BlockAttribute::class)?.block?.let { block ->
            val compiler = IRCompiler(fileContext, method)
            compiler.process(block)

            method.attributes += CodeAttribute(compiler.instructions)
        }
    }

    override fun afterFileContext(fileContext: FileContext) {
        MutableTypePool.pool.mapNotNull { (namePkgTArgs, type) ->
            val (namePkg, _) = namePkgTArgs
            if (namePkg.startsWith("aurum.lang.Fn"))
                return@mapNotNull type
            null
        }.forEach {
            fileContext.classes[it] = ClassDeclCtx(AurumParser.ClassDeclContext(null, 0))
        }
    }
}