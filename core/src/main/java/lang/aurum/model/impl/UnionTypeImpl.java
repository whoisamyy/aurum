package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

public record UnionTypeImpl(
        Type[] types
) implements UnionType {
    @NotNull
    @Override
    public UnionType asArray(int dimensions) {
        return new UnionTypeImpl(types);
    }

    @NotNull
    @Override
    public UnionType withTypeArguments(TypeArgument[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @NotNull
    @Override
    public UnionType withTypeArguments(Type[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }
}
