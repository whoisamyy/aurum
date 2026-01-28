package lang.aurum.parsing;

import lang.aurum.Argument;
import lang.aurum.Command;
import lang.aurum.Parameters;

import java.nio.file.Path;
import java.util.Set;

@Command(names = {"-cp", "--classpath"})
public final class ClassPathArgument implements Argument {
    @Parameters
    public Set<Path> classPath = Set.of();

    public Set<Path> classPath() {
        return classPath;
    }
}
