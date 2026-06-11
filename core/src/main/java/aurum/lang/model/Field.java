package aurum.lang.model;

import org.jetbrains.annotations.NotNull;

public interface Field extends Member {
    @NotNull Type type();

    @Override
    default @NotNull Field asGenericallyUntypedMember() {
        return owner().withDefaultTypeArguments().findField(name()).orElseThrow();
    }
}
