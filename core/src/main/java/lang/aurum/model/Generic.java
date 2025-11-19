package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface Generic {
    @NotNull Optional<TypeParameter[]> typeParameters();
    @NotNull Optional<TypeArgument[]> typeArguments();
    @NotNull Generic withTypeArguments(TypeArgument[] typeArguments);
    @NotNull Generic withTypeArguments(Type[] typeArguments);
}
