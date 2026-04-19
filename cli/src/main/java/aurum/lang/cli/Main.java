package aurum.lang.cli;

import aurum.lang.cli.args.Argument;
import aurum.lang.cli.args.Arguments;
import aurum.lang.cli.args.SourcesArgument;
import aurum.lang.cli.args.Target;
import aurum.lang.cli.aurum.lang.cli.ArgumentGroup;
import aurum.lang.cli.aurum.lang.cli.CLI;
import aurum.lang.cli.aurum.lang.cli.Command;
import aurum.lang.compiler.frontend.OutputStage;
import aurum.lang.compiler.frontend.Pipeline;
import aurum.lang.compiler.frontend.stages.CompilationContext;
import aurum.lang.compiler.frontend.stages.CompilationData;
import aurum.lang.compiler.frontend.stages.Source;
import aurum.lang.compiler.frontend.stages.SourceRetrievingStage;
import aurum.lang.compiler.frontend.stages.analyzing.*;
import aurum.lang.compiler.frontend.stages.parsing.ParsingStage;
import aurum.lang.compiler.frontend.stages.parsing.TokenizationStage;

import java.util.Set;

@Command
public class Main {
    @ArgumentGroup(types = {
//            ClassPathArgument.class,
//            ParserArgument.PrintStackTrace.class,
//            ParserArgument.GenerateIRFiles.class,
//            ParserArgument.VerboseIR.class,
//            OptimisationLevel.O1.class,
//            OptimisationLevel.O2.class,
//            OptimisationLevel.O3.class,
//            OptimisationLevel.ORAW.class,
//            OptimisationLevel.Custom.class,
            Target.class,
            SourcesArgument.class,
    })
    public Set<Argument> arguments;

    private static SourcesArgument sourcesArg = null;
    private static SourcesArgument getSourcesArg() {
        if (sourcesArg == null) {
            SourcesArgument result = Arguments.get(SourcesArgument.class);
            if (result == null) throw new IllegalStateException("todo");
            sourcesArg = result;
        }

        return sourcesArg;
    }

    public static void main(String[] args) {
        Main main = new Main();
        new CLI<>(main, args).parseArgs();
        Arguments.addArguments(main.arguments);

        var ctx = new CompilationContext();
        ctx.put(new Source(getSourcesArg().sources().stream().findFirst().orElseThrow()));
        ctx.put(new CompilationData(getSourcesArg().workDir(), getSourcesArg().output(), 0));

        ctx.registerContext();

        Pipeline.registerStage(SourceRetrievingStage.class);
        Pipeline.registerStage(TokenizationStage.class);
        Pipeline.registerStage(ParsingStage.class);
        Pipeline.registerStage(PackageProcessingStage.class);
        Pipeline.registerStage(ImportProcessingStage.class);
        Pipeline.registerStage(TypeDefiningStage.class);
        Pipeline.registerStage(TypeProcessingStage.class);
        Pipeline.registerStage(MemberProcessingStage.class);
        Pipeline.registerStage(OperatorResolvingStage.class);
        Pipeline.registerStage(OutputStage.class);

        Pipeline.run();
    }
}
