package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

public interface Member extends Accessible, Attributable {
    @NotNull Type owner();
    @NotNull String name();
}
