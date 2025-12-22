package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface Attribute {
    @NotNull String name();
    @NotNull Map<String, Object> values();
    default boolean isVisible() {
        return true;
    }
}
