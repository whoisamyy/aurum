package aurum.lang.model;

import aurum.lang.model.impl.ParameterImpl;
import org.jetbrains.annotations.NotNull;

public interface Parameter extends Attributable {
    @NotNull String name();
    @NotNull Type type();

    static @NotNull Parameter of(@NotNull String name, @NotNull Type type) {
        return new ParameterImpl(name, type);
    }

    static @NotNull Parameter of(
            @NotNull String name,
            @NotNull Type type,
            @NotNull Attribute @NotNull[] attributes
    ) {
        return new ParameterImpl(name, type, attributes);
    }
}
