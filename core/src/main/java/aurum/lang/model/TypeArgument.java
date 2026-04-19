package aurum.lang.model;

import aurum.lang.model.impl.TypeArgumentImpl;
import org.jetbrains.annotations.NotNull;

public interface TypeArgument {
    String name();
    Type bound();

    static TypeArgument of(@NotNull String name, @NotNull Type bound) {
        return new TypeArgumentImpl(name, bound);
    }
}
