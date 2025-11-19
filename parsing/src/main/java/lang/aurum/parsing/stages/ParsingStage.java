package lang.aurum.parsing.stages;

import org.jetbrains.annotations.NotNull;

///**
// * @param <T> Context type
// */
//    <T extends AbstractParsingContext>
public abstract class ParsingStage {
    protected final ParsingContext parsingContext;

    protected ParsingStage(ParsingContext parsingContext) {
        this.parsingContext = parsingContext;
    }

    public abstract void execute();
}
