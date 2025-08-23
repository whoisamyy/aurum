package lang.aurum.model;

public interface Method extends Member, Generic {
    Type owner();
    Type returnType();
    Parameter[] parameters();
    Type[] exceptions();

    @Override
    Method withTypeArguments(TypeArgument[] typeArguments);

    @Override
    Method withTypeArguments(Type[] typeArguments);
}
