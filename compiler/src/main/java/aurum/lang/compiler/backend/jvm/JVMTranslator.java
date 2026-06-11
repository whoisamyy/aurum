package aurum.lang.compiler.backend.jvm;

import aurum.lang.attribute.ConstantPoolAttribute;
import aurum.lang.compiler.backend.Translator;
import aurum.lang.compiler.frontend.attribute.AttributeExtensionsKt;
import aurum.lang.compiler.frontend.model.MutableModelsKt;
import aurum.lang.compiler.frontend.stages.ProcessedType;
import aurum.lang.ir.*;
import aurum.lang.ir.Instruction;
import aurum.lang.ir.Opcode;
import aurum.lang.model.*;
import aurum.lang.model.attribute.ExtensionAttribute;
import aurum.lang.model.attribute.LambdaMethodAttribute;
import org.jetbrains.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.*;
import java.lang.invoke.LambdaMetafactory;
import java.util.*;

public final class JVMTranslator extends Translator<byte[]> {
    private final ConstantPool cp;

    public JVMTranslator(ProcessedType type) {
        super(type);
        this.cp = Objects.requireNonNull(AttributeExtensionsKt.get(this.type.attributes(), ConstantPoolAttribute.class))
                         .getConstantPool();
    }

    @Override
    public byte[] translate() {
        ClassFile cf = ClassFile.of();

        byte[] bytes = cf.build(JVMUtils.classDescOf(type), cb -> {
            cb.withFlags(type.intFlags());
            if (type.superClass() != null) {
                //noinspection DataFlowIssue
                cb.withSuperclass(JVMUtils.classDescOf(type.superClass()));
            }
            cb.withInterfaceSymbols(
                    Arrays.stream(type.interfaces())
                          .map(JVMUtils::classDescOf)
                          .toArray(ClassDesc[]::new)
            );

            for (Field field : type.fields()) {
                cb.withField(field.name(), JVMUtils.classDescOf(field.type()), field.intFlags());
            }

            for (Method method : type.methods()) {
                if (!method.owner().equals(type)) {
                    continue;
                }

                cb.withMethod(
                        method.name(),
                        JVMUtils.methodTypeDescOf(method),
                        method.intFlags(),
                        mb -> {
                            if (AttributeExtensionsKt.contains(method.attributes(), CodeAttribute.class))
                                mb.withCode(codeCb -> new InstructionTranslator(codeCb, method).translate());
                        }
                );
            }
        });


        // todo: jvm optimizations and cfg walker and etc
        ClassModel model;
        JVMOptimizer optimizer;
        do {
            model = cf.parse(bytes);
            optimizer = new JVMOptimizer();
            bytes = cf.transformClass(model, ClassTransform.transformingMethodBodies(optimizer));
        } while (optimizer.hasChanged());

        return bytes;
    }

    private class InstructionTranslator extends Translator<CodeBuilder> {
        private final CodeBuilder cb;
        private final Method method;
        private final List<Instruction> instructions;
        private final List<Local> locals = new ArrayList<>();
        private final Map<String, Label> labels = new HashMap<>();

        private record Local(String name, Type type, int slot) {}

        InstructionTranslator(CodeBuilder cb, Method method) {
            super(JVMTranslator.this.processedType);
            this.cb = cb;
            this.method = method;
            this.instructions = Objects.requireNonNull(
                    AttributeExtensionsKt.get(method.attributes(), CodeAttribute.class)
            ).getCode();

            int slot = 0;
            if (!method.isStatic()) {
                locals.add(new Local("this", method.owner(), slot));
                slot += localSlots(method.owner());
            }
            for (Parameter parameter : method.parameters()) {
                locals.add(new Local(parameter.name(), parameter.type(), slot));
                slot += localSlots(parameter.type());
            }
        }

        @Override
        public CodeBuilder translate() {
            for (Instruction inst : instructions) {
                translateInstruction(inst);
            }
            return cb;
        }

        private void translateInstruction(Instruction inst) {
            switch (inst) {
                case Null n -> {
                    cb.aconst_null();
                    store(n.getTarget(), Types.OBJECT);
                }
                case Move move -> store(move.getTarget(), resolveRValue(move.getRef()));
                case BinaryOp binaryOp -> {
                    Type leftType = resolveRValue(binaryOp.getLeft());
                    Type rightType = resolveRValue(binaryOp.getRight());
                    Opcode irOpcode = Operators.OPERATOR_OPCODES.get(binaryOp.getOperator());
                    if (irOpcode == null) {
                        throw new IllegalStateException("Unsupported binary operator: " + binaryOp.getOperator());
                    }
                    store(binaryOp.getTarget(), resolveOperator(irOpcode, leftType, rightType));
                }
                case Neg neg -> {
                    Type valueType = resolveRValue(neg.getRef());
                    switch (valueType.typeKind()) {
                        case BOOLEAN -> cb.ifThenElse(
                                java.lang.classfile.Opcode.IFEQ,
                                CodeBuilder::iconst_1,
                                CodeBuilder::iconst_0
                        );
                        case INT -> cb.with(OperatorInstruction.of(java.lang.classfile.Opcode.INEG));
                        case LONG -> cb.with(OperatorInstruction.of(java.lang.classfile.Opcode.LNEG));
                        case FLOAT -> cb.with(OperatorInstruction.of(java.lang.classfile.Opcode.FNEG));
                        case DOUBLE -> cb.with(OperatorInstruction.of(java.lang.classfile.Opcode.DNEG));
                        default -> throw new IllegalStateException("Cannot negate type: " + valueType);
                    }
                    store(neg.getTarget(), valueType);
                }
                case Jump jump -> cb.goto_(label(jump.getLabel().getName()));
                case JumpIf jumpIf -> {
                    resolveRValue(jumpIf.getCond());
                    cb.ifne(label(jumpIf.getLabel().getName()));
                }
                case JumpIfN jumpIfN -> {
                    resolveRValue(jumpIfN.getCond());
                    cb.ifeq(label(jumpIfN.getLabel().getName()));
                }
                case Return ret -> resolveReturn(ret.getValue());
                case Throw thr -> {
                    resolveRValue(thr.getRef());
                    cb.athrow();
                }
                case TryBegin _, TryEnd _, Catch _ -> { /* exception tables not yet emitted */ }
                case Call call -> translateCall(call);
                case CallMethod callMethod -> translateCallMethod(callMethod);
                case CallVirtual callVirtual -> translateCallVirtual(callVirtual);
                case InvokeConstructor init -> translateInvokeConstructor(init);
                case Closure closure -> {
                    Method m = cp.dereference((SingleMethodRef) closure.getFunc());
                    loadMethodRef(m);
                    store(closure.getTarget(), functionalType(m));
                }
                case New n -> {
                    Type classType = cp.dereference(n.getClassRef());
                    cb.new_(JVMUtils.classDescOf(classType));
                    store(n.getTarget(), classType);
                }
                case NewArray newArray -> {
                    Type elementType = cp.dereference(newArray.getElementType());
                    resolveRValue(newArray.getSizeRef());
                    emitNewArray(elementType);
                    store(newArray.getTarget(), elementType.asArray(1));
                }
                case GetField getField -> {
                    resolveRValue(getField.getObj());
                    Field field = cp.dereference(getField.getField());
                    if (field.owner().isArray()) {
                        cb.arraylength();
                    } else {
                        cb.getfield(
                                JVMUtils.classDescOf(field.owner()),
                                field.name(),
                                JVMUtils.classDescOf(field.type())
                        );
                    }
                    store(getField.getTarget(), field.type());
                }
                case PutField putField -> {
                    resolveRValue(putField.getObj());
                    resolveRValue(putField.getValue());
                    Field field = cp.dereference(putField.getField());
                    cb.putfield(
                            JVMUtils.classDescOf(field.owner()),
                            field.name(),
                            JVMUtils.classDescOf(field.type())
                    );
                }
                case GetStatic getStatic -> {
                    Field field = cp.dereference(getStatic.getField());
                    cb.getstatic(
                            JVMUtils.classDescOf(field.owner()),
                            field.name(),
                            JVMUtils.classDescOf(field.type())
                    );
                    store(getStatic.getTarget(), field.type());
                }
                case GetMethod getMethod -> {
                    Method actual = cp.dereference((SingleMethodRef) getMethod.getMethod());
                    loadMethodRef(actual);
                    store(getMethod.getTarget(), functionalType(actual));
                }
                case ArrayLoad arrayLoad -> {
                    Type arrayType = resolveRValue(arrayLoad.getArray());
                    resolveRValue(arrayLoad.getIndex());
                    emitArrayLoad(arrayType);
                    store(arrayLoad.getTarget(), arrayElementType(arrayType));
                }
                case ArrayStore arrayStore -> {
                    Type arrayType = resolveRValue(arrayStore.getArray());
                    resolveRValue(arrayStore.getIndex());
                    resolveRValue(arrayStore.getValue(), arrayElementType(arrayType));
                    emitArrayStore(arrayType);
                }
                case Cast cast -> {
                    Type from = resolveRValue(cast.getRef());
                    Type to = cp.dereference(cast.getType());
                    if (!from.isPrimitive() && to instanceof PrimitiveType primitive) {
                        cb.with(
                                TypeCheckInstruction.of(
                                        java.lang.classfile.Opcode.CHECKCAST,
                                        JVMUtils.classDescOf(primitive.boxed())
                                )
                        );

                        store(cast.getTarget(), primitive.boxed());
                        return;
                    } else if (from.isPrimitive() && to.isPrimitive()) {
                        cb.with(ConvertInstruction.of(from.typeKind(), to.typeKind()));
                    } else if (!from.isPrimitive() && !to.isPrimitive()) {
                        cb.checkcast(JVMUtils.classDescOf(to));
                    } else {
                        throw new IllegalStateException("Cannot cast between primitive and reference types");
                    }
                    store(cast.getTarget(), to);
                }
                case InstanceOf instanceOf -> {
                    resolveRValue(instanceOf.getRef());
                    Type checkType = instanceTypeForInstanceOf(cp.dereference(instanceOf.getType()));
                    cb.instanceOf(JVMUtils.classDescOf(checkType));
                    store(instanceOf.getTarget(), Types.BOOLEAN);
                }
                case TypeOf typeOf -> {
                    resolveRValue(typeOf.getRef());
                    cb.invokevirtual(ConstantDescs.CD_Object, "getClass", MethodTypeDesc.of(ConstantDescs.CD_Class));
                    store(typeOf.getTarget(), Type.ofClass(Class.class));
                }
                case LabelInst labelInst -> cb.labelBinding(label(labelInst.getLabel().getName()));
                case Nop _ -> cb.nop();
                case GetMember _, Switch _ ->
                        throw new UnsupportedOperationException("Unsupported instruction: " + inst);
                default -> throw new IllegalStateException("Unexpected instruction: " + inst);
            }
        }

        private void translateCall(Call call) {
            if (!(call.getMethod() instanceof SingleMethodRef ref)) {
                throw new UnsupportedOperationException("Method group references not yet supported");
            }
            Method actual = cp.dereference(ref);
            emitArguments(call.getArgs(), actual);
            cb.invokestatic(
                    JVMUtils.classDescOf(actual.owner()),
                    actual.name(),
                    JVMUtils.methodTypeDescOf(actual),
                    actual.owner().isInterface()
            );
            store(call.getTarget(), actual.returnType());
        }

        private void translateCallMethod(CallMethod callMethod) {
            if (!(callMethod.getMethod() instanceof SingleMethodRef ref)) {
                throw new UnsupportedOperationException("Method group references not yet supported");
            }
            Method actual = cp.dereference(ref);
            resolveRValue(callMethod.getObj());
            emitArguments(callMethod.getArgs(), actual);
            cb.invokespecial(
                    JVMUtils.classDescOf(actual.owner()),
                    actual.name(),
                    JVMUtils.methodTypeDescOf(actual)
            );
            store(callMethod.getTarget(), actual.returnType());
        }

        private void translateCallVirtual(CallVirtual callVirtual) {
            if (!(callVirtual.getMethod() instanceof SingleMethodRef ref)) {
                throw new UnsupportedOperationException("Method group references not yet supported");
            }
            Method actual = cp.dereference(ref);
            resolveRValue(callVirtual.getObj());
            emitArguments(callVirtual.getArgs(), actual);
            if (actual.owner().isInterface()) {
                cb.invokeinterface(
                        JVMUtils.classDescOf(actual.owner()),
                        actual.name(),
                        JVMUtils.methodTypeDescOf(actual)
                );
            } else {
                cb.invokevirtual(
                        JVMUtils.classDescOf(actual.owner()),
                        actual.name(),
                        JVMUtils.methodTypeDescOf(actual)
                );
            }
            store(callVirtual.getTarget(), actual.returnType());
        }

        private void translateInvokeConstructor(InvokeConstructor init) {
            Type objType = resolveRValue(init.getObj());
            List<Type> argTypes = init.getArgs().stream().map(this::resolveRValue).toList();
            Method constructor = objType.findMethod("<init>", argTypes.toArray(Type[]::new))
                                        .orElseThrow(() -> new IllegalStateException(
                                                "Constructor not found on " + objType.toUsageString()
                                        ));
//            emitArguments(init.getArgs(), constructor);
            cb.invokespecial(
                    JVMUtils.classDescOf(objType),
                    "<init>",
                    JVMUtils.methodTypeDescOf(constructor)
            );
        }

        private void emitArguments(List<? extends RValue> args, Method target) {
            for (int i = 0; i < args.size(); i++) {
                resolveRValue(args.get(i), target.parameters()[i].type());
            }
        }

        private void resolveReturn(@Nullable RValue value) {
            Type retType = resolveRValue(value);
            if (retType == Types.VOID) {
                cb.return_();
            } else {
                cb.return_(retType.typeKind());
            }
        }

        private void store(LValue target, Type type) {
            if (type == Types.VOID) {
                return;
            }
            if (target instanceof Reference ref) {
                if (ref.getName().equals("_")) {
                    cb.pop();
                } else {
                    Local local = local(ref.getName(), type);
                    cb.storeLocal(local.type.typeKind(), local.slot);
                }
            }
        }

        private Type resolveRValue(@Nullable RValue rvalue) {
            return resolveRValue(rvalue, null);
        }

        private Type resolveRValue(@Nullable RValue rvalue, @Nullable Type requiredType) {
            if (rvalue == null) {
                return Types.VOID;
            }
            return switch (rvalue) {
                case ConstantPoolRef constRef -> resolveConstant(constRef, requiredType);
                case Reference.Super _ -> {
                    Local local = local("this");
                    cb.loadLocal(local.type.typeKind(), local.slot);
                    yield method.owner().superClass() != null ? method.owner().superClass() : Types.OBJECT;
                }
                case Reference.This _ -> {
                    Local local = local("this");
                    cb.loadLocal(local.type.typeKind(), local.slot);
                    yield local.type;
                }
                case Reference ref -> {
                    Local local = local(ref.getName());
                    cb.loadLocal(local.type.typeKind(), local.slot);
                    if (local.type.isPrimitive())
                        yield boxIfNeeded(((PrimitiveType) local.type), requiredType);
                    yield local.type;
                }
                case NullRef _ -> {
                    cb.aconst_null();
                    yield Types.OBJECT;
                }
                default -> throw new IllegalStateException("Unexpected RValue: " + rvalue.getClass());
            };
        }

        private Type resolveConstant(ConstantPoolRef constRef, @Nullable Type requiredType) {
            Object value = cp.dereference(constRef);
            return switch (value) {
                case Method m -> {
                    loadMethodRef(m);
                    yield functionalType(m);
                }
                case Field f -> {
                    if (!f.isStatic()) {
                        throw new IllegalStateException("Cannot reference non-static field from static context");
                    }
                    cb.getstatic(
                            JVMUtils.classDescOf(f.owner()),
                            f.name(),
                            JVMUtils.classDescOf(f.type())
                    );
                    yield f.type();
                }
                case Type t -> {
                    cb.ldc(JVMUtils.classDescOf(t));
                    yield Type.ofClass(Class.class);
                }
                case Integer i -> {
                    cb.ldc(i);
                    yield boxIfNeeded(Types.INT, requiredType);
                }
                case Long l -> {
                    cb.ldc(l);
                    yield boxIfNeeded(Types.LONG, requiredType);
                }
                case Float f -> {
                    cb.ldc(f);
                    yield boxIfNeeded(Types.FLOAT, requiredType);
                }
                case Double d -> {
                    cb.ldc(d);
                    yield boxIfNeeded(Types.DOUBLE, requiredType);
                }
                case Boolean b -> {
                    cb.ldc(b ? 1 : 0);
                    yield boxIfNeeded(Types.BOOLEAN, requiredType);
                }
                case Byte b -> {
                    cb.ldc(b.intValue());
                    yield boxIfNeeded(Types.BYTE, requiredType);
                }
                case Short s -> {
                    cb.ldc(s.intValue());
                    yield boxIfNeeded(Types.SHORT, requiredType);
                }
                case Character c -> {
                    cb.ldc((int) c);
                    yield boxIfNeeded(Types.CHAR, requiredType);
                }
                case String s -> {
                    cb.ldc(s);
                    yield Types.STRING;
                }
                default -> throw new IllegalStateException("Unsupported constant pool value: " + value.getClass());
            };
        }

        private Type boxIfNeeded(PrimitiveType primitive, @Nullable Type requiredType) {
            if (requiredType != null && !requiredType.isPrimitive()) {
                cb.invokestatic(
                        JVMUtils.classDescOf(primitive.boxed()),
                        "valueOf",
                        MethodTypeDesc.of(JVMUtils.classDescOf(primitive.boxed()), JVMUtils.classDescOf(primitive))
                );
                return primitive.boxed();
            }
            return primitive;
        }

        private void loadMethodRef(Method method) {
            Type fnType = functionalType(method);
            ClassDesc interfaceDesc = JVMUtils.classDescOf(fnType);
            MethodTypeDesc indyReturnType = MethodTypeDesc.of(interfaceDesc);
            MethodTypeDesc methodSig = JVMUtils.methodTypeDescOf(method);

            var bsm = MethodHandleDesc.ofMethod(
                    DirectMethodHandleDesc.Kind.STATIC,
                    ClassDesc.ofDescriptor(LambdaMetafactory.class.descriptorString()),
                    "metafactory",
                    MethodTypeDesc.of(
                            ConstantDescs.CD_CallSite,
                            ConstantDescs.CD_MethodHandles_Lookup,
                            ConstantDescs.CD_String,
                            ConstantDescs.CD_MethodType,
                            ConstantDescs.CD_MethodType,
                            ConstantDescs.CD_MethodHandle,
                            ConstantDescs.CD_MethodType
                    )
            );

            DirectMethodHandleDesc target = MethodHandleDesc.ofMethod(
                    method.isStatic() ? DirectMethodHandleDesc.Kind.STATIC : DirectMethodHandleDesc.Kind.VIRTUAL,
                    JVMUtils.classDescOf(method.owner()),
                    method.name(),
                    methodSig
            );

            Method abstractMethod = Arrays.stream(fnType.withDefaultTypeArguments().methods())
                                          .filter(Accessible::isAbstract)
                                          .findFirst()
                                          .orElseThrow();
            MethodTypeDesc samDesc = JVMUtils.lambdaMethodTypeDescOf(abstractMethod);

            cb.invokedynamic(DynamicCallSiteDesc.of(
                    bsm,
                    "invoke",
                    indyReturnType,
                    samDesc,
                    target,
                    methodSig
            ));
        }

        private Type functionalType(Method method) {
            if (AttributeExtensionsKt.contains(method.attributes(), LambdaMethodAttribute.class)) {
                return Objects.requireNonNull(
                        AttributeExtensionsKt.get(method.attributes(), LambdaMethodAttribute.class)
                ).getFunctionalInterface();
            }
            return MutableModelsKt.getType(method);
        }

        private void emitNewArray(Type elementType) {
            if (elementType.isPrimitive()) {
                cb.newarray(elementType.typeKind());
            } else if (elementType instanceof ArrayType<?> array) {
                cb.multianewarray(JVMUtils.classDescOf(array), array.arrayDimensions());
            } else {
                cb.anewarray(JVMUtils.classDescOf(elementType));
            }
        }

        private void emitArrayLoad(Type arrayType) {
            if (!(arrayType instanceof ArrayType<?> array)) {
                throw new IllegalStateException("Expected array type, got: " + arrayType);
            }
            cb.arrayLoad(array.componentType().typeKind());
        }

        private void emitArrayStore(Type arrayType) {
            if (!(arrayType instanceof ArrayType<?> array)) {
                throw new IllegalStateException("Expected array type, got: " + arrayType);
            }

            cb.arrayStore(array.componentType().typeKind());
        }

        private static Type arrayElementType(Type arrayType) {
            if (arrayType instanceof ArrayType<?> array) {
                return array.componentType();
            }
            throw new IllegalStateException("Expected array type, got: " + arrayType);
        }

        private static Type instanceTypeForInstanceOf(Type type) {
            if (!type.isPrimitive()) {
                return type;
            }
            if (AttributeExtensionsKt.contains(type.attributes(), ExtensionAttribute.class)) {
                type = Objects.requireNonNull(AttributeExtensionsKt.get(type.attributes(), ExtensionAttribute.class))
                              .getType();
            }
            if (type instanceof PrimitiveType primitive) {
                return primitive.boxed();
            }
            return type;
        }

        private Label label(String name) {
            return labels.computeIfAbsent(name, _ -> cb.newLabel());
        }

        private Local local(String name) {
            return locals.stream()
                         .filter(v -> v.name.equals(name))
                         .findFirst()
                         .orElseThrow(() -> new IllegalStateException("Unknown local: " + name));
        }

        private Local local(String name, Type type) {
            return locals.stream()
                         .filter(v -> v.name.equals(name))
                         .findFirst()
                         .orElseGet(() -> {
                             Local created = new Local(name, type, nextSlot());
                             locals.add(created);
                             return created;
                         });
        }

        private int nextSlot() {
            int slot = 0;
            for (Local local : locals) {
                slot = Math.max(slot, local.slot + localSlots(local.type));
            }
            return slot;
        }

        private Type resolveOperator(Opcode irOpcode, Type left, Type right) {
            NumericCoercion coercion = coerceNumeric(left, right);
            TypeKind kind = coercion.kind();

            switch (irOpcode) {
                case Add -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IADD, kind)));
                case Sub -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.ISUB, kind)));
                case Mul -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IMUL, kind)));
                case Div -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IDIV, kind)));
                case Mod -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IREM, kind)));
                case And -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IAND, kind)));
                case Or -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IOR, kind)));
                case Xor -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IXOR, kind)));
                case Shl -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.ISHL, kind)));
                case Shr -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.ISHR, kind)));
                case Ushr -> cb.with(OperatorInstruction.of(numericOpcode(java.lang.classfile.Opcode.IUSHR, kind)));
                case CmpEq -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPEQ, java.lang.classfile.Opcode.IFEQ, kind);
                    return Types.BOOLEAN;
                }
                case CmpNe -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPNE, java.lang.classfile.Opcode.IFNE, kind);
                    return Types.BOOLEAN;
                }
                case CmpLt -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPLT, java.lang.classfile.Opcode.IFLT, kind);
                    return Types.BOOLEAN;
                }
                case CmpLe -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPLE, java.lang.classfile.Opcode.IFLE, kind);
                    return Types.BOOLEAN;
                }
                case CmpGt -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPGT, java.lang.classfile.Opcode.IFGT, kind);
                    return Types.BOOLEAN;
                }
                case CmpGe -> {
                    emitComparison(java.lang.classfile.Opcode.IF_ICMPGE, java.lang.classfile.Opcode.IFGE, kind);
                    return Types.BOOLEAN;
                }
                default -> throw new IllegalStateException("Unsupported IR opcode: " + irOpcode);
            }
            return coercion.type();
        }

        private record NumericCoercion(Type type, TypeKind kind) {}

        private NumericCoercion coerceNumeric(Type left, Type right) {
            int leftRank = numericRank(left);
            int rightRank = numericRank(right);
            if (leftRank < 0) {
                return new NumericCoercion(left, left.typeKind());
            }
            if (rightRank < 0) {
                return new NumericCoercion(right, right.typeKind());
            }
            if (leftRank > rightRank) {
                cb.with(ConvertInstruction.of(right.typeKind(), left.typeKind()));
                return new NumericCoercion(left, left.typeKind());
            }
            if (leftRank < rightRank) {
                cb.swap();
                cb.with(ConvertInstruction.of(left.typeKind(), right.typeKind()));
                cb.swap();
                return new NumericCoercion(right, right.typeKind());
            }
            return new NumericCoercion(left, left.typeKind());
        }

        private static int numericRank(Type type) {
            if (type.equals(Types.BYTE)) return 1;
            if (type.equals(Types.SHORT)) return 2;
            if (type.equals(Types.INT)) return 3;
            if (type.equals(Types.LONG)) return 4;
            if (type.equals(Types.FLOAT)) return 5;
            if (type.equals(Types.DOUBLE)) return 6;
            return -1;
        }

        private static java.lang.classfile.Opcode numericOpcode(
                java.lang.classfile.Opcode base,
                TypeKind kind
        ) {
            int offset = switch (kind.asLoadable()) {
                case BOOLEAN, BYTE, CHAR, SHORT, INT -> 0;
                case LONG -> 1;
                case FLOAT -> 2;
                case DOUBLE -> 3;
                default -> throw new IllegalStateException("Not a numeric type kind: " + kind);
            };
            int target = base.bytecode() + offset;
            return Arrays.stream(java.lang.classfile.Opcode.values())
                         .filter(op -> op.bytecode() == target)
                         .findFirst()
                         .orElseThrow(() -> new IllegalStateException("No opcode for " + base + " and " + kind));
        }

        private void emitComparison(
                java.lang.classfile.Opcode intOpcode,
                java.lang.classfile.Opcode floatOpcode,
                TypeKind kind
        ) {
            switch (kind) {
                case LONG -> {
                    cb.lcmp();
                    cb.ifThenElse(floatOpcode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case FLOAT -> {
                    cb.fcmpg();
                    cb.ifThenElse(floatOpcode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case DOUBLE -> {
                    cb.dcmpg();
                    cb.ifThenElse(floatOpcode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                }
                case INT -> cb.ifThenElse(intOpcode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                default -> cb.ifThenElse(floatOpcode, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
            }
        }
    }
}
