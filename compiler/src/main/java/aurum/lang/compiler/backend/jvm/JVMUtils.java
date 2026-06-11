package aurum.lang.compiler.backend.jvm;

import aurum.lang.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

public final class JVMUtils {
    private JVMUtils() {}

    public static @NotNull ClassDesc classDescOf(@NotNull Type type) {
        return switch (type) {
            case PrimitiveType primitive -> ClassDesc.ofDescriptor(primitive.jvmName());
            case UnionType ut -> classDescOf(ut.superClass());
            case IntersectionType it -> classDescOf(it.types()[0]);
            case ArrayType<?> arrayType -> ClassDesc.ofDescriptor(
                    "[".repeat(arrayType.arrayDimensions()) + referenceDescriptor(arrayType.componentType())
            );
            default -> ClassDesc.ofDescriptor("L" + type.fullName().replace('.', '/') + ";");
        };
    }

    private static String referenceDescriptor(Type type) {
        if (type instanceof PrimitiveType primitive) {
            return primitive.jvmName();
        }
        if (type instanceof ArrayType<?> array) {
            return "[".repeat(array.arrayDimensions()) + referenceDescriptor(array.componentType());
        }
        return "L" + type.fullName().replace('.', '/') + ";";
    }

    public static @NotNull MethodTypeDesc methodTypeDescOf(@NotNull Method method) {
        Type returnType = method.returnType() instanceof TemplateType ? Types.OBJECT : method.returnType();
        return MethodTypeDesc.of(
                classDescOf(returnType),
                java.util.Arrays.stream(method.parameters())
                        .map(Parameter::type)
                        .map(t -> t instanceof TemplateType ? Types.OBJECT : t)
                        .map(JVMUtils::classDescOf)
                        .toList()
        );
    }

    public static @NotNull MethodTypeDesc lambdaMethodTypeDescOf(@NotNull Method method) {
        Type returnType = method.returnType() instanceof TemplateType
                ? Types.OBJECT
                : (method.returnType() instanceof PrimitiveType primitiveType
                  ? primitiveType.boxed()
                  : method.returnType());

        return MethodTypeDesc.of(
                classDescOf(returnType),
                java.util.Arrays.stream(method.parameters())
                        .map(Parameter::type)
                        .map(t -> t instanceof PrimitiveType primitiveType ? primitiveType.boxed() : t)
                        .map(t -> t instanceof TemplateType ? Types.OBJECT : t)
                        .map(JVMUtils::classDescOf)
                        .toList()
        );
    }
}
