package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface Generic {
    @NotNull Optional<TypeParameter @NotNull []> typeParameters();
    @NotNull Optional<TypeArgument @NotNull []> typeArguments();
    @NotNull Generic withTypeArguments(TypeArgument @NotNull [] typeArguments);
    @NotNull Generic withTypeArguments(Type @NotNull [] typeArguments);
}
