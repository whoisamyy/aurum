package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public interface Member extends Accessible, Attributable {
    @NotNull Type owner();
    @NotNull String name();
}
