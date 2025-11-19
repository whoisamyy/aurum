package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

public interface Method extends Member, Generic {
    @NotNull Type returnType();
    @NotNull Parameter[] parameters();
    @NotNull Type[] exceptions();

    @Override
    @NotNull
    Method withTypeArguments(TypeArgument[] typeArguments);

    @Override
    @NotNull
    Method withTypeArguments(Type[] typeArguments);
}
