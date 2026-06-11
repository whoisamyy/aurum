package aurum.lang.model;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public interface Generic {
    @NotNull TypeParameter @NotNull [] typeParameters();
    @NotNull TypeArgument @NotNull [] typeArguments();
    @NotNull Generic withTypeArguments(TypeArgument @NotNull [] typeArguments);
    @NotNull Generic withTypeArguments(Type @NotNull [] typeArguments);
    default @NotNull Generic withDefaultTypeArguments() {
        return withTypeArguments(Arrays.stream(typeParameters()).map(TypeParameter::bound).toArray(Type[]::new));
    }
}
