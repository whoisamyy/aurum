package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

public interface Field extends Member {
    @NotNull Type type();
}
