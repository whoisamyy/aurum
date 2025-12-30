package lang.aurum.parsing;

import lang.aurum.TypeConverter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

public interface Source {
    Path source();

    class SourceConverter implements TypeConverter<Source> {
        @Override
        public @NotNull Source convert(@NotNull String string) {
            var path = Path.of(string);
            if (Files.isDirectory(path)) {
                return new DirectorySource(path);
            }
            return new FileSource(path);
        }
    }
}
