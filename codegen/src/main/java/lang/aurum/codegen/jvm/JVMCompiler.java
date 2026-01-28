package lang.aurum.codegen.jvm;

import lang.aurum.codegen.Compiler;
import lang.aurum.codegen.jvm.util.Utils;
import lang.aurum.ir.CodeAttribute;
import lang.aurum.ir.ConstantPool;
import lang.aurum.model.Field;
import lang.aurum.model.Method;
import lang.aurum.model.Type;
import lang.aurum.parsing.AurumErrorKt;
import lang.aurum.parsing.attribute.AttributeExtensionsKt;
import lang.aurum.parsing.model.MutableMethod;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record JVMCompiler(Type type, ConstantPool constantPool) implements Compiler {
    @Override
    public boolean compile(Path output) throws IOException {
        ClassFile cf = ClassFile.of();
        var clazz = cf
                 .build(Utils.classDescOf(type), cb -> {
                     cb.withFlags(type.intFlags());
                     cb.withSuperclass(Utils.classDescOf(type.superClass()));
                     type.interfaces()
                         .map(interfaces -> Arrays.stream(interfaces)
                                                  .map(Utils::classDescOf)
                                                  .toList())
                         .ifPresent(cb::withInterfaceSymbols);

                     for (Field field : type.fields()) {
                         cb.withField(
                                 field.name(),
                                 Utils.classDescOf(field.type()),
                                 field.intFlags()
                         );
                     }

                     for (Method method : type.methods()) {
                         if (!(method instanceof MutableMethod))
                             continue;

                         cb.withMethod(
                                 method.name(),
                                 Utils.methodTypeDescOf(method),
                                 method.intFlags(),
                                 mb -> {
                                     if (!AttributeExtensionsKt.contains(method.attributes(), CodeAttribute.class))
                                         return;

                                     //noinspection DataFlowIssue
                                     mb.withCode(new Translator(
                                             method,
                                             cb,
                                             constantPool,
                                             AttributeExtensionsKt.get(method.attributes(), CodeAttribute.class)
                                                    .getCode()
                                     ));
                                 }
                         );
                     }
                 });

        if (!Files.exists(output)) { Files.createFile(output); }

        // kinda temporary solution, but it works
        clazz = cleanup(cf, cf.parse(clazz));

//        verifyBytecode(output, cf, clazz);

        Files.write(output, clazz);
        return true;
    }

    private static void verifyBytecode(Path output, ClassFile cf, byte[] clazz) {
        List<VerifyError> verifyErrors = cf.verify(clazz);
        if (!verifyErrors.isEmpty())
            throw AurumErrorKt.aurumError(
                    "Error while compiling to JVM target: " + verifyErrors,
                    output,
                    null
            );
    }

    private byte[] cleanup(ClassFile cf, ClassModel model) {
        return cf.transformClass(model, (classBuilder, el) -> {
            classBuilder.withFlags(type.intFlags());
            if (el instanceof MethodModel method) {
                var locals = getLocalUsages(method);

                classBuilder.withMethod(
                        method.methodName(),
                        method.methodType(),
                        method.flags().flagsMask(),
                        mb -> {
                            if (method.code().isEmpty())
                                return;

                            mb.withCode(cb -> {
                                @SuppressWarnings("OptionalGetWithoutIsPresent")
                                var code = method.code().get();

                                List<CodeElement> elements = code.elementList();
                                for (int i = 0; i < elements.size(); i++) {
                                    CodeElement prev;
                                    if (i - 1 < 0)
                                        prev = null;
                                    else prev = elements.get(i - 1);

                                    var cur = elements.get(i);

                                    CodeElement next;
                                    if (i + 1 >= elements.size())
                                        next = null;
                                    else next = elements.get(i + 1);

                                    if ((!(cur instanceof StoreInstruction si)
                                            || !(next instanceof LoadInstruction li)
                                            || si.slot() != li.slot()
                                            || locals.get(si.slot()) > 1) && (!(prev instanceof StoreInstruction si1)
                                            || !(cur instanceof LoadInstruction li1)
                                            || si1.slot() != li1.slot()
                                            || locals.get(si1.slot()) > 1)) {
                                        if (cur instanceof StoreInstruction si && locals.get(si.slot()) == null) {
                                            cb.with(StackInstruction.of(Opcode.POP));
                                        } else cb.with(cur);
                                    }
                                }
                            });
                        }
                );
            }
        });
    }

    private Map<Integer, Integer> getLocalUsages(MethodModel method) {
        return method.code().map(code -> {
            Map<Integer, Integer> locals = new HashMap<>();
            for (CodeElement el : code.elementList()) {
                if (el instanceof LoadInstruction li) {
                    locals.computeIfPresent(li.slot(), (_, old) -> old+1);
                    locals.putIfAbsent(li.slot(), 1);
                }
            }

            return locals;
        }).orElse(new HashMap<>());
    }
}
