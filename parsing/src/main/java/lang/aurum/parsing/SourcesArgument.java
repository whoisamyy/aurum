package lang.aurum.parsing;

import lang.aurum.Argument;
import lang.aurum.Command;
import lang.aurum.Option;
import lang.aurum.Parameters;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;

@Command
public final class SourcesArgument implements Argument {
    @Option(names = {"-wd", "--workdir"})
    public Path workDir = Path.of("");
    @Parameters()
    public Set<Source> sources = Set.of();
    @Option(names = {"-r", "-rd"})
    public boolean recursiveDirectories;

    public @NotNull Path workDir() {
        return workDir;
    }

    public @NotNull Set<@NotNull Source> sources() {
        return sources;
    }

    public boolean recursiveDirectories() {
        return recursiveDirectories;
    }
}

