package lang.aurum.cli;

import kotlin.collections.CollectionsKt;
import lang.aurum.*;
import lang.aurum.codegen.Compiler;
import lang.aurum.codegen.Target;
import lang.aurum.ir.IrFile;
import lang.aurum.model.Type;
import lang.aurum.parsing.*;
import lang.aurum.parsing.stages.optimisation.OptimisationLevel;
import org.antlr.v4.runtime.ParserRuleContext;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import static kotlin.io.path.PathsKt.getNameWithoutExtension;
import static lang.aurum.parsing.Utils.pathOf;

@Command
public class Main {
    @ArgumentGroup(types = {
            ClassPathArgument.class,
            ParserArgument.PrintStackTrace.class,
            ParserArgument.GenerateIRFiles.class,
            ParserArgument.VerboseIR.class,
            OptimisationLevel.O1.class,
            OptimisationLevel.O2.class,
            OptimisationLevel.O3.class,
            OptimisationLevel.ORAW.class,
            Target.class,
            OptimisationLevel.Custom.class,
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
        CLI.registerTypeConverter(Source.class, Source.SourceConverter.class);
        new CLI<>(main, args).parseArgs();
        Arguments.addArguments(main.arguments);

        Set<IrFile> files = Parser.parse();

        boolean useVerboseIR = Arguments.contains(ParserArgument.VerboseIR.class);

        if (Arguments.get(Target.class) == Target.IR || Arguments.contains(ParserArgument.GenerateIRFiles.class)) {
            generateIRFiles(files, useVerboseIR);
        }

        final Target target = Arguments.getOrDefault(Target.class, Target.JVM);

        for (IrFile f : files) {
            compileFilesToTarget(f, target);
        }
    }

    private static void compileFilesToTarget(IrFile file, Target target) {
        Path output = getSourcesArg().workDir().resolve("output")
                                .resolve(pathOf(CollectionsKt.minus(file.getSrcPath(), getSourcesArg().workDir())));
        Path dirs = pathOf(CollectionsKt.dropLast(CollectionsKt.toList(output), 1));

        for (Type type : file.getClasses()) {
            try {
                var newFileName = getNewFileName(type, output, target);
                output = dirs;
                for (String dir : type.pkg().split("\\.")) {
                    output = output.resolve(dir);
                }
                output = output.resolve(newFileName);
                if (!Files.exists(output)) {
                    try {
                        Files.createDirectories(output.getParent());
                    } catch (IOException e) {
                        throw AurumErrorKt.aurumError(e.getMessage(), (String) null, output);
                    }
                }

                Compiler.get(type, file.getConstantPool(), Objects.requireNonNull(target))
                        .compile(output);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NotNull
    private static String getNewFileName(Type t, Path output, Target target) {
        String fileName = getNameWithoutExtension(output);
        fileName = t.className().equals(fileName)
                ? fileName
                : /*fileName + "$" + */ t.className();

        fileName += target.extension;

        return fileName;
    }

    private static void generateIRFiles(Set<IrFile> files, boolean useVerbose) {
        for (var file : files) {
            IrFileWriter fileWriter;
            if (useVerbose)
                fileWriter = new VerboseIrFileWriter(file);
            else
                fileWriter = new ByteIrFileWriter(file);

            Path output = getSourcesArg().workDir().resolve("output")
                                    .resolve(Utils.pathOf(CollectionsKt.minus(file.getSrcPath(), getSourcesArg().workDir())));

            output = output.getParent().resolve(getNameWithoutExtension(output)+".aur");

            Path dirs = Utils.pathOf(CollectionsKt.dropLast(CollectionsKt.toList(output), 1));
            if (!Files.exists(output)) {
                try {
                    Files.createDirectories(dirs);
                } catch (IOException e) {
                    AurumErrorKt.throwAurumError(e.getMessage(), (ParserRuleContext) null, null);
                }
            }

            fileWriter.write(output);
        }
    }
}
