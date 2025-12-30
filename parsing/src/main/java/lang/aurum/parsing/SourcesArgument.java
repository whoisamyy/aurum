package lang.aurum.parsing;

import lang.aurum.Argument;
import lang.aurum.Command;
import lang.aurum.Option;
import lang.aurum.Parameters;

import java.nio.file.Path;
import java.util.Set;

@Command
public final class SourcesArgument implements Argument {
    @Option(names = {"-wd", "--workdir"})
    public Path workDir;
    @Parameters()
    public Set<Source> sources;
    @Option(names = {"-r", "-rd"})
    public boolean recursiveDirectories;

    public Path workDir() {
        return workDir;
    }

    public Set<Source> sources() {
        return sources;
    }

    public boolean recursiveDirectories() {
        return recursiveDirectories;
    }
}

