package lang.aurum.model;

import lang.aurum.model.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public interface Generic {
    @NotNull Optional<TypeParameter @NotNull []> typeParameters();
    @NotNull Optional<TypeArgument @NotNull []> typeArguments();
    @NotNull Generic withTypeArguments(TypeArgument @NotNull [] typeArguments);
    @NotNull Generic withTypeArguments(Type @NotNull [] typeArguments);
    default @NotNull Generic withDefaultTypeArguments() {
        return withTypeArguments(typeParameters().map(params ->
                Arrays.stream(params)
                      .map(TypeParameter::bound)
                      .toArray(Type[]::new)
        ).orElse(Utils.EMPTY_TYPES));
    }
}
