package aurum.lang.model;

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

    @Override
    default @NotNull Method asDefaultTypedMember() {
        return owner().withDefaultTypeArguments().findMethodExact(
                name(),
                returnType(),
                Arrays.stream(parameters())
                      .map(p -> {
                          if (!(p.type() instanceof TemplateType template))
                              return p.type();

                          if (Arrays.stream(typeArguments())
                                    .map(TypeArgument::name)
                                    .toList()
                                    .contains(template.className())) {
                              return Arrays.stream(typeArguments())
                                    .filter(arg -> arg.name().equals(template.className()))
                                    .findFirst().orElseThrow().bound();
                          } else if (Arrays.stream(owner().typeParameters())
                                           .map(TypeParameter::name)
                                           .toList()
                                           .contains(template.className())) {
                              return Arrays.stream(owner().typeParameters())
                                           .filter(n -> n.name().equals(template.className()))
                                           .findFirst().orElseThrow().bound();
                          } else {
                              return p.type();
                          }
                      }).toArray(Type[]::new)
        ).orElseThrow();
    }

    default @NotNull String signature() {
        return "%s(%s)%s".formatted(
                name(),
                String.join(
                        ",",
                        Arrays.stream(parameters())
                              .map(Parameter::type)
                              .map(Type::toUsageString)
                              .toList()
                ),
                returnType().toUsageString()
        );
    }

    static @NotNull Method of(java.lang.reflect.Method method) {
        return Type.ofClass(method.getDeclaringClass())
            .findMethod(method.getName(), Arrays.stream(method.getParameterTypes())
                                                .map(Type::ofClass)
                                                .toArray(Type[]::new))
            .orElseThrow();
    }
}
