package lang.aurum.model.factory;

import lang.aurum.model.*;
import lang.aurum.model.impl.MethodImpl;
import lang.aurum.model.impl.ParameterImpl;
import lang.aurum.model.impl.Utils;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

public class MethodFactory {
    public static Method ofMethod(java.lang.reflect.Method method) {
        return new MethodImpl(
                TypeFactory.ofClass(method.getDeclaringClass()),
                method.getName(),
                TypeFactory.ofClass(method.getReturnType()),
                parametersOf(method.getParameters()),
                Arrays.stream(method.getExceptionTypes()).map(TypeFactory::ofClass).toArray(Type[]::new),
                method.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.factory.Utils.getTypeParameters(method),
                Optional.empty(), // empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );
    }

    public static Method ofConstructor(Constructor<?> constructor) {
        return new MethodImpl(
                TypeFactory.ofClass(constructor.getDeclaringClass()),
                "<init>",
                PrimitiveType.VOID,
                parametersOf(constructor.getParameters()),
                Arrays.stream(constructor.getExceptionTypes()).map(TypeFactory::ofClass).toArray(Type[]::new),
                constructor.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.factory.Utils.getTypeParameters(constructor),
                Optional.empty(), // left empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );
    }

    private static Parameter[] parametersOf(java.lang.reflect.Parameter[] parameters) {
        return Arrays.stream(parameters)
                .map(
                        p -> new ParameterImpl(p.getName(), TypeFactory.ofClass(p.getType()), Utils.EMPTY_ATTRIBUTES)
                )
                .toArray(Parameter[]::new);
    }
}
