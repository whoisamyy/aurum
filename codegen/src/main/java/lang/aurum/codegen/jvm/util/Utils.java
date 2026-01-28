package lang.aurum.codegen.jvm.util;

import lang.aurum.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.Comparator;

public final class Utils {
    private Utils() {}

    public static ClassDesc classDescOf(Type type) {
        if (type instanceof PrimitiveType primitive)
            return ClassDesc.ofDescriptor(primitive.jvmName());

        if (type instanceof ArrayType<?> arrayType) {
            return ClassDesc.ofDescriptor("%sL%s;".formatted(
                    "[".repeat(arrayType.arrayDimensions()),
                    type.fullName().replace('.', '/')
            ));
        }

        return ClassDesc.ofDescriptor("L%s;".formatted(type.fullName().replace('.', '/')));
    }

    public static ConstantPool constantPoolOf(lang.aurum.ir.ConstantPool cp) {
        var cpBuilder = ConstantPoolBuilder.of();
        var sortedEntries = cp.getConstantPool().entrySet().stream()
                              .sorted(Comparator.comparingInt(e -> e.getKey().getRef()))
                              .toList();

        for (var entry : sortedEntries) {
            var value = entry.getValue();

            switch (value) {
                case Method m -> cpBuilder.methodTypeEntry(methodTypeDescOf(m));
                case Field f -> cpBuilder.fieldRefEntry(classDescOf(f.owner()), f.name(), classDescOf(f.type()));
                case Type t -> cpBuilder.classEntry(classDescOf(t));
                case Integer _,
                     Short _,
                     Byte _ -> cpBuilder.intEntry(((Number) value).intValue());
                case Boolean b -> cpBuilder.intEntry(b ? 1 : 0);
                case Float f -> cpBuilder.floatEntry(f);
                case Double d -> cpBuilder.doubleEntry(d);
                case Long l -> cpBuilder.longEntry(l);
                case String s -> cpBuilder.stringEntry(s);
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        }

        return cpBuilder;
    }

    public static MethodTypeDesc methodTypeDescOf(Method method) {
        return MethodTypeDesc.of(
                classDescOf(method.returnType() instanceof TemplateType
                        ? Types.OBJECT
                        : method.returnType()),
                Arrays.stream(method.parameters())
                      .map(Parameter::type)
                      .map(pType -> pType instanceof TemplateType
                              ? Types.OBJECT
                              : pType
                      )
                      .map(Utils::classDescOf)
                      .toList()
        );
    }

    public static int toIntFlags(@NotNull AccessFlag[] accessFlags) {
        return Arrays.stream(accessFlags)
                     .reduce(0,
                             (i, f) -> i | f.mask(),
                             (i1, i2) -> i1 | i2
                     );
    }
}
