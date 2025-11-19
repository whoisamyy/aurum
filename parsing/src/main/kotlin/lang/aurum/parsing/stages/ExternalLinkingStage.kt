package lang.aurum.parsing.stages

class ExternalLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute() {
        parsingContext.files.forEach {
            process(LinkingContext(it))
        }
    }

    private fun process(linkingContext: LinkingContext) {

    }
}