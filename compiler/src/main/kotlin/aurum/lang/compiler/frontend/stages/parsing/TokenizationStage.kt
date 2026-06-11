package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.Files
import aurum.lang.compiler.frontend.stages.SourceRetrievingStage
import aurum.lang.compiler.frontend.stages.Stage

class TokenizationStage : Stage() {
    val files = input<Files>()
    val out = output<Files>()

    init {
        dependsOn<SourceRetrievingStage>()
    }

    override fun execute() {
        out.set(Files(files.get().map {
            it.also { f ->
                f.tokens = Tokenizer(it.contents).parse()
            }
        }))
    }
}