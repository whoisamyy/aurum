package lang.aurum.model;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public interface Method extends Member, Generic {
    @NotNull Type returnType();
    @NotNull Parameter[] parameters();
    @NotNull Type[] exceptions();

    @Override
    @NotNull Method withTypeArguments(TypeArgument @NotNull [] typeArguments);
    @Override
    @NotNull Method withTypeArguments(Type @NotNull [] typeArguments);
    @Override
    @NotNull Method withDefaultTypeArguments();

    static @NotNull Method of(java.lang.reflect.Method method) {
        return Type.ofClass(method.getDeclaringClass())
            .findMethod(method.getName(), Arrays.stream(method.getParameterTypes())
                                                .map(Type::ofClass)
                                                .toArray(Type[]::new))
            .orElseThrow();
    }
}
