package lang.aurum.model.impl;

import lang.aurum.model.Type;
import lang.aurum.model.TypeArgument;
import lang.aurum.model.UnionType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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
    public UnionType withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @NotNull
    @Override
    public UnionType withTypeArguments(Type @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @Override
    public @NotNull UnionType withDefaultTypeArguments() {
        return UnionType.ofTypeModels(Arrays.stream(types).map(Type::withDefaultTypeArguments).toArray(Type[]::new));
    }
}
