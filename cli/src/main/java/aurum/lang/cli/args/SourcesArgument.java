package aurum.lang.cli.args;

import aurum.lang.cli.Command;
import aurum.lang.cli.Option;
import aurum.lang.cli.Parameters;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;

@SuppressWarnings("CanBeFinal")
@Command
public final class SourcesArgument implements Argument {
    @Parameters
    public Set<Path> sources = Set.of();

    @Option(names = {"-wd", "--workdir"})
    public Path workDir = Path.of("");

    @Option(names = {"-o", "--output"})
    public Path output = Path.of("output/");


    public @NotNull Path workDir() {
        return workDir;
    }

    public @NotNull Set<@NotNull Path> sources() {
        return sources;
    }

    public Path output() {
        return output;
    }
}