package lang.aurum.model.impl;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Optional;

public record MethodImpl(
        Type owner,
        String name,
        Type returnType,
        Parameter[] parameters,
        Type[] exceptions,
        AccessFlag[] accessFlags,
        Optional<TypeParameter[]> typeParameters,
        Optional<TypeArgument[]> typeArguments,
        Attribute[] attributes
) implements Method {
    @NotNull
    public Method withTypeArguments(TypeArgument @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, owner, typeArguments);
    }

    @NotNull
    @Override
    public Method withTypeArguments(Type @NotNull [] typeArguments) {
        return Utils.applyTypeArguments(this, owner, typeArguments);
    }

    @Override
    public @NotNull Method withDefaultTypeArguments() {
        return this; // TODO
    }

    @Override
    public @NotNull String toString() {
        return returnType.toUsageString()
                + " " + owner.toUsageString() + "." + name
                + "("
                + String.join(
                        ", ",
                        Arrays.stream(parameters).map(Parameter::type).map(Type::toUsageString).toList()
                ) + ")";
    }
}
