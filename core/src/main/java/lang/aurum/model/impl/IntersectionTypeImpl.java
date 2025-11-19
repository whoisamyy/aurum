package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

public record IntersectionTypeImpl(
        Type[] types
) implements IntersectionType {
    @NotNull
    @Override
    public IntersectionType withTypeArguments(TypeArgument[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }


    @NotNull
    @Override
    public IntersectionType withTypeArguments(Type[] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }
}
