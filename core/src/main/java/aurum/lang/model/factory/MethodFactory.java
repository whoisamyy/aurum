package aurum.lang.model.factory;

import aurum.lang.model.Method;
import aurum.lang.model.Parameter;
import aurum.lang.model.Type;
import aurum.lang.model.Types;
import aurum.lang.model.impl.MethodImpl;
import aurum.lang.model.impl.ParameterImpl;
import aurum.lang.model.impl.Utils;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class MethodFactory {
    public static Method ofMethod(java.lang.reflect.Method method) {
        MethodImpl ret = new MethodImpl(
                TypeFactory.ofType(method.getDeclaringClass()),
                method.getName(),
                TypeFactory.ofType(method.getGenericReturnType()),
                parametersOf(method.getParameters()),
                Arrays.stream(method.getGenericExceptionTypes()).map(TypeFactory::ofType).toArray(Type[]::new),
                method.accessFlags().toArray(AccessFlag[]::new),
                aurum.lang.model.factory.Utils.getTypeParameters(method),
                Utils.EMPTY_TYPE_ARGUMENTS, // empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );

        aurum.lang.model.factory.Utils.processTypeParameters(ret, method);

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
                aurum.lang.model.factory.Utils.getTypeParameters(constructor),
                Utils.EMPTY_TYPE_ARGUMENTS, // left empty because of the way method generic parametrization is handled
                Utils.EMPTY_ATTRIBUTES
        );

        aurum.lang.model.factory.Utils.processTypeParameters(ret, constructor);

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
