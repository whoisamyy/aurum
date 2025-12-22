package lang.aurum.parsing.stages.coderesolution

import lang.aurum.ir.CodeAttribute
import lang.aurum.model.Method
import lang.aurum.parsing.attribute.get
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.stages.BlockAttribute
import lang.aurum.parsing.stages.FileContext
import lang.aurum.parsing.stages.ParsingContext
import lang.aurum.parsing.stages.ParsingStage

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
}