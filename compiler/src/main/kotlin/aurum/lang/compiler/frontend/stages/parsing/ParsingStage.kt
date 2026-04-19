package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.Files
import aurum.lang.compiler.frontend.stages.Stage

class ParsingStage : Stage() {
    val files = input<Files>()
    val asts = output<Files>()

    init {
        dependsOn<TokenizationStage>()
    }

    override fun execute() {
        asts.set(Files(
            files.get().map {
                it.ast = Parser(it.tokens.filter { token -> token !is Token.EndLine && token !is Token.Comment })
                    .parse()
                it
            }
        ))
    }
}