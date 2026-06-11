package aurum.lang.model;

import kotlin.Pair;
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

    // needs better naming
    /// Converts this method into its generically untyped method equivalent. <br>
    /// <br>
    /// This means that all type arguments will become unresolved and all method parameters and return type will become
    /// [TemplateType] instances
    /// <br>
    /// <br>
    /// Example:
    /// ```
    /// List<Integer> List.<Integer>of(Integer, Integer, Integer)
    /// // becomes
    /// List<E> List.<E>of(E, E, E)
    /// ```
    @Override
    default @NotNull Method asGenericallyUntypedMember() {
        return owner().getRawType().findMethod(
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

    default @NotNull Pair<@NotNull Integer, @NotNull Float> getFitDegree(Type[] argTypes) {
        @NotNull Parameter[] params = this.parameters();
        if (params.length != argTypes.length)
            return new Pair<>(Integer.MAX_VALUE, Float.MAX_VALUE);
        if (params.length == 0)
            return new Pair<>(0, 0f);

        boolean areSubclasses = true;
        for (int i = 0; i < params.length; i++) {
            var p = params[i];
            var argType = argTypes[i];

            areSubclasses &= argType.isSubclassOf(p.type());
        }

        int[] inheritanceDistances = new int[params.length];
        for (int i = 0; i < params.length; i++) {
            var p = params[i];
            var argType = argTypes[i];

            inheritanceDistances[i] = p.type().getInheritanceDistance(argType);
        }

        if (Arrays.stream(inheritanceDistances).anyMatch(i -> i < 0))
            return new Pair<>(Integer.MAX_VALUE, Float.MAX_VALUE);

        // suppressed because only way this is possible is if parameter count is 0 which is already checked
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        float avg = (float) Arrays.stream(inheritanceDistances).average().getAsDouble();
        int sum = Arrays.stream(inheritanceDistances).sum();

        return new Pair<>(sum, avg);
    }

    static @NotNull Method of(java.lang.reflect.Method method) {
        return Type.ofClass(method.getDeclaringClass())
            .findMethod(method.getName(), Arrays.stream(method.getParameterTypes())
                                                .map(Type::ofClass)
                                                .toArray(Type[]::new))
            .orElseThrow();
    }
}
