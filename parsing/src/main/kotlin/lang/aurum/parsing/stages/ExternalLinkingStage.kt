package lang.aurum.parsing.stages

class ExternalLinkingStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(fileContext: FileContext) {
        process(LinkingContext(fileContext))
    }

    private fun process(linkingContext: LinkingContext) {

    }
}