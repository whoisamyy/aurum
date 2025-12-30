package lang.aurum.parsing;

import java.nio.file.Path;

public record FileSource (
        Path file
) implements Source {
    @Override
    public Path source() {
        return file;
    }
}
