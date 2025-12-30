package lang.aurum.parsing;

import java.nio.file.Path;

public record DirectorySource(
        Path directory
) implements Source {
    @Override
    public Path source() {
        return directory;
    }
}
