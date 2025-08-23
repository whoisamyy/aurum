package lang.aurum.model;

import java.util.Optional;

public interface Generic {
    Optional<TypeParameter[]> typeParameters();
    Optional<TypeArgument[]> typeArguments();
    Generic withTypeArguments(TypeArgument[] typeArguments);
    Generic withTypeArguments(Type[] typeArguments);
}
