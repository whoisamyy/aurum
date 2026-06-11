package aurum.lang.model.impl;

import aurum.lang.model.IntersectionType;
import aurum.lang.model.Type;
import aurum.lang.model.TypeArgument;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public record IntersectionTypeImpl(
        Type[] types
) implements IntersectionType {
    @NotNull
    @Override
    public IntersectionType withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @NotNull
    @Override
    public IntersectionType withTypeArguments(Type @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, typeArguments);
    }

    @Override
    public @NotNull IntersectionType withDefaultTypeArguments() {
        return IntersectionType.ofTypeModels(Arrays.stream(types).map(Type::withDefaultTypeArguments).toArray(Type[]::new));
    }
}
