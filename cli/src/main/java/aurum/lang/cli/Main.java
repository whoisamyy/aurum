package aurum.lang.cli;

import aurum.lang.cli.args.Argument;
import aurum.lang.cli.args.Arguments;
import aurum.lang.cli.args.SourcesArgument;
import aurum.lang.cli.args.Target;
import aurum.lang.compiler.frontend.Pipeline;
import aurum.lang.compiler.frontend.stages.*;
import aurum.lang.compiler.frontend.stages.analyzing.*;
import aurum.lang.compiler.frontend.stages.compiling.CompilingStage;
import aurum.lang.compiler.frontend.stages.linking.LinkerInjectionStage;
import aurum.lang.compiler.frontend.stages.linking.LinkingStage;
import aurum.lang.compiler.frontend.stages.optimization.OptimizationStage;
import aurum.lang.compiler.frontend.stages.output.OutputStage;
import aurum.lang.compiler.frontend.stages.parsing.ParsingStage;
import aurum.lang.compiler.frontend.stages.parsing.TokenizationStage;
import aurum.lang.compiler.frontend.stages.translating.TranslationStage;
import aurum.lang.compiler.frontend.stages.translating.TranslatorInjectionStage;
import aurum.lang.compiler.frontend.stages.typeresolving.TypeResolverInjectionStage;

import java.util.Objects;
import java.util.Set;

@Command
public class Main {
    @ArgumentGroup(types = {
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
        ctx.put(new CompilationData(getSourcesArg().workDir(), getSourcesArg().output(), 3));
        Target target = Objects.requireNonNullElse(Arguments.get(Target.class), Target.JVM);
        ctx.put(new TargetArtifact(target.name(), target.extension));

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
        Pipeline.registerStage(CompilingStage.class);
        Pipeline.registerStage(TypeResolverInjectionStage.class);
        Pipeline.registerStage(LinkingStage.class);
        Pipeline.registerStage(LinkerInjectionStage.class);
        Pipeline.registerStage(OptimizationStage.class);
        Pipeline.registerStage(TranslatorInjectionStage.class);
        Pipeline.registerStage(TranslationStage.class);
        Pipeline.registerStage(PrimaryConstructorResolvingStage.class);

        Pipeline.run();
    }
}
