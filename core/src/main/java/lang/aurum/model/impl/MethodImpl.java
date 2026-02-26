package lang.aurum.model.impl;

import lang.aurum.model.*;
import lang.aurum.model.util.ParametrizedMethodPool;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.AccessFlag;
import java.util.Arrays;

public record MethodImpl(
        Type owner,
        String name,
        Type returnType,
        Parameter[] parameters,
        Type[] exceptions,
        AccessFlag[] accessFlags,
        TypeParameter[] typeParameters,
        TypeArgument[] typeArguments,
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
        return typeArguments.length > 0
                ? ParametrizedMethodPool.getBaseMethod(this)
                : this;

//        return owner.withDefaultTypeArguments()
//                    .findMethodExact(name, returnType, parameters)
//                    .orElseThrow()
//                    .withTypeArguments(
//                            typeParameters
//                                    .map(tps ->
//                                            Arrays.stream(tps)
//                                                  .map(TypeParameter::bound)
//                                                  .toArray(Type[]::new)
//                                    )
//                                    .orElse(Utils.EMPTY_TYPES)
//                    );
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodImpl method)) return false;

        return owner().toUsageString().equals(method.owner().toUsageString())
                && name().equals(method.name())
                && returnType().equals(method.returnType())
                && Arrays.equals(parameters(), method.parameters())
                && Arrays.equals(typeArguments(), method.typeArguments());
    }

    @Override
    public int hashCode() {
        int result = owner().toUsageString().hashCode();
        result = 31 * result + name().hashCode();
        result = 31 * result + returnType().hashCode();
        result = 31 * result + Arrays.hashCode(parameters());
        result = 31 * result + Arrays.hashCode(typeArguments());
        return result;
    }
}
