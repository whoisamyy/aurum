package lang.aurum.parsing;

import lang.aurum.Arguments;
import lang.aurum.ir.IrFile;
import lang.aurum.parsing.stages.FileContext;
import lang.aurum.parsing.stages.ParsingContext;
import lang.aurum.parsing.stages.Pipeline;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class Parser {
    public static Set<IrFile> parse() {
        var sourcesArg = Arguments.get(SourcesArgument.class);
        var cpArg = Arguments.getOrDefault(ClassPathArgument.class, new ClassPathArgument());
        var sources = getSources();

        assert sourcesArg != null;
        var fileCtxs = new LinkedHashSet<FileContext>();

        ParsingContext ctx = new ParsingContext(sourcesArg.workDir(), cpArg.classPath(), fileCtxs);

        fileCtxs.addAll(sources.stream().map(src -> FileContext.Companion.ofPath(ctx, src)).toList());

        new Pipeline().execute(ctx);

        return ctx.getFiles().stream().map(f -> {
            var types = new ArrayList<>(f.getClasses().keySet());

            return new IrFile(
                    f.getPath(),
                    f.getConstantPool(),
                    types,
                    List.of()
            );
        }).collect(Collectors.toSet());
    }

    private static Set<Path> getSources() {
        SourcesArgument sources = Arguments.INSTANCE.get(SourcesArgument.class);
        if (sources == null)
            throw new IllegalStateException("todo");

        return sources.sources()
                      .stream().map(src -> switch (src) {
                    case DirectorySource(Path dir) -> {
                        var srcs = new HashSet<>(Arrays.stream(
                                Objects.requireNonNull(
                                        dir.toFile()
                                           .listFiles(pathname -> pathname.toString().endsWith(".au"))
                                )
                        ).map(File::toPath).toList());
                        if (sources.recursiveDirectories()) {
                            srcs.addAll(getRecursiveContents(dir));
                        }
                        yield srcs;
                    }
                    case FileSource(Path file) -> Set.of(file);
                    default -> throw new IllegalStateException("Unexpected value: " + src);
                }).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    private static Set<Path> getRecursiveContents(Path dir) {
        var sources = new HashSet<>(
                Arrays.stream(
                        Objects.requireNonNull(
                                dir.toFile().listFiles((_, fileName) -> fileName.endsWith(".au"))
                        )
                )
                      .map(File::toPath)
                      .toList()
        );

        for (File file : Objects.requireNonNull(dir.toFile().listFiles((file, _) -> file.isDirectory()))) {
            sources.addAll(getRecursiveContents(file.toPath()));
        }

        return sources;
    }
}
