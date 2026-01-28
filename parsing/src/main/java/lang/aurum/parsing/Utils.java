package lang.aurum.parsing;

import java.nio.file.Path;
import java.util.List;

public final class Utils {
    private Utils() {}

    public static Path pathOf(List<Path> paths) {
        return Path.of(paths.getFirst().toString(), paths.subList(1, paths.size()).stream().map(Path::toString).toArray(String[]::new));
    }
}
