package lang.aurum.model.factory;

import lang.aurum.model.Method;
import lang.aurum.model.Parameter;
import lang.aurum.model.Type;
import lang.aurum.model.Types;
import lang.aurum.model.impl.MethodImpl;
import lang.aurum.model.impl.ParameterImpl;
import lang.aurum.model.impl.Utils;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

public final class MethodFactory {
    public static Method ofMethod(java.lang.reflect.Method method) {
        MethodImpl ret = new MethodImpl(
                TypeFactory.ofType(method.getDeclaringClass()),
                method.getName(),
                TypeFactory.ofType(method.getGenericReturnType()),
                parametersOf(method.getParameters()),
                Arrays.stream(method.getGenericExceptionTypes()).map(TypeFactory::ofType).toArray(Type[]::new),
                method.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.factory.Utils.getTypeParameters(method),
                Optional.empty(), // empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );

        lang.aurum.model.factory.Utils.processTypeParameters(ret, method);

        return ret;
    }

    public static Method ofConstructor(Constructor<?> constructor) {
        MethodImpl ret = new MethodImpl(
                TypeFactory.ofClass(constructor.getDeclaringClass()),
                "<init>",
                Types.VOID,
                parametersOf(constructor.getParameters()),
                Arrays.stream(constructor.getGenericExceptionTypes()).map(TypeFactory::ofType).toArray(Type[]::new),
                constructor.accessFlags().toArray(AccessFlag[]::new),
                lang.aurum.model.factory.Utils.getTypeParameters(constructor),
                Optional.empty(), // left empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );

        lang.aurum.model.factory.Utils.processTypeParameters(ret, constructor);

        return ret;
    }

    private static Parameter[] parametersOf(java.lang.reflect.Parameter[] parameters) {
        return Arrays.stream(parameters)
                .map(
                        p -> new ParameterImpl(p.getName(), TypeFactory.ofType(p.getParameterizedType()), Utils.EMPTY_ATTRIBUTES)
                )
                .toArray(Parameter[]::new);
    }
}
