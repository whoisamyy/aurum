package lang.aurum.codegen.jvm;

import kotlin.Pair;
import lang.aurum.attribute.ExtensionAttribute;
import lang.aurum.codegen.jvm.util.Utils;
import lang.aurum.ir.*;
import lang.aurum.ir.Instruction;
import lang.aurum.model.*;
import lang.aurum.parsing.attribute.AttributeExtensionsKt;
import lang.aurum.parsing.model.MutableModelsKt;
import org.jetbrains.annotations.Nullable;

import java.lang.classfile.*;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.constant.*;
import java.lang.invoke.LambdaMetafactory;
import java.util.*;
import java.util.function.Consumer;

public class Translator implements Consumer<CodeBuilder> {
    private final Method method;
    private final ClassBuilder classBuilder;
    private final ConstantPool constantPool;
    private final List<Instruction> instructions;

    private final List<Variable> variables = new ArrayList<>();
    private record Variable (String name, Type type, int index) {
        public Variable(String name, int index) {
            this(name, Types.OBJECT, index);
        }
    }

    private final Map<String, Label> labels = new HashMap<>();

    private CodeBuilder codeBuilder;

    public Translator(Method method, ClassBuilder classBuilder, ConstantPool constantPool, List<Instruction> instructions) {
        this.method = method;
        this.classBuilder = classBuilder;
        this.constantPool = constantPool;
        this.instructions = instructions;

        if (!this.method.isStatic()) {
            this.variables.add(new Variable("this", method.owner(), 0));
        }
        for (Parameter parameter : this.method.parameters()) {
            this.variables.add(new Variable(parameter.name(), parameter.type(), this.variables.size()));
        }
    }

    private void translate() {
        for (Instruction inst : instructions) {
            switch (inst) {
                case Null null_ -> {
                    codeBuilder.aconst_null();
                    store(null_.getTarget(), Types.OBJECT);
                }
                case Move move -> {
                    var type = resolveRValue(move.getRef());
                    store(move.getTarget(), type);
                }
                case BinaryOp binaryOp -> {
                    var leftType = resolveRValue(binaryOp.getLeft());
                    var rightType = resolveRValue(binaryOp.getRight());
                    var resultType = resolveOperator(binaryOp.getOperator(), leftType, rightType);
                    store(binaryOp.getTarget(), resultType);
                }
                case Neg neg -> {
                    var type = resolveRValue(neg.getRef());
                    // Neg instruction handles arithmetic negation (unary minus)
                    switch (type.typeKind()) {
                        case BOOLEAN -> codeBuilder.ifThenElse(Opcode.IFEQ, CodeBuilder::iconst_1, CodeBuilder::iconst_0);
                        case INT -> codeBuilder.with(OperatorInstruction.of(Opcode.INEG));
                        case LONG -> codeBuilder.with(OperatorInstruction.of(Opcode.LNEG));
                        case FLOAT -> codeBuilder.with(OperatorInstruction.of(Opcode.FNEG));
                        case DOUBLE -> codeBuilder.with(OperatorInstruction.of(Opcode.DNEG));
                        default -> throw new IllegalStateException("Cannot negate type: " + type);
                    }
                    store(neg.getTarget(), type);
                }
                case Jump jump -> {
                    var label = getLabel(jump.getLabel().getName());
                    codeBuilder.goto_(label);
                }
                case JumpIf jumpIf -> {
                    resolveRValue(jumpIf.getCond());
                    codeBuilder.ifne(getLabel(jumpIf.getLabel().getName()));
                }
                case Return return_ -> {
                    resolveReturn(return_.getValue());
                }
                case Throw throw_ -> {
                    resolveRValue(throw_.getRef());
                    codeBuilder.athrow();
                }
                case TryBegin tryBegin -> {
                    // Exception handling will be implemented later
                }
                case TryEnd tryEnd -> {
                    // Exception handling will be implemented later
                }
                case Catch catch_ -> {
                    // Exception handling will be implemented later
                }
                case Call call -> {
                    MethodRef methodRef = call.getMethod();
                    if (methodRef instanceof SingleMethodRef ref) {
                        Method actual = constantPool.dereference(ref);
                        for (int i = 0; i < call.getArgs().size(); i++) {
                            var rValue = call.getArgs().get(i);
                            resolveRValue(rValue, actual.parameters()[i].type());
                        }

                        var mtd = Utils.methodTypeDescOf(actual);
                        codeBuilder.invokestatic(Utils.classDescOf(actual.owner()), actual.name(), mtd, actual.owner().isInterface());
                        store(call.getTarget(), actual.returnType());
                    } else {
                        throw new UnsupportedOperationException("Method group references not yet supported");
                    }
                }
                case CallMethod callMethod -> {
                    MethodRef methodRef = callMethod.getMethod();
                    if (methodRef instanceof SingleMethodRef ref) {
                        Method actual = constantPool.dereference(ref);
                        resolveRValue(callMethod.getObj());
                        for (int i = 0; i < callMethod.getArgs().size(); i++) {
                            var rValue = callMethod.getArgs().get(i);
                            resolveRValue(rValue, actual.parameters()[i].type());
                        }

                        var mtd = Utils.methodTypeDescOf(actual);
                        codeBuilder.invokespecial(Utils.classDescOf(actual.owner()), actual.name(), mtd);
                        store(callMethod.getTarget(), actual.returnType());
                    } else {
                        throw new UnsupportedOperationException("Method group references not yet supported");
                    }
                }
                case CallVirtual callVirtual -> {
                    MethodRef methodRef = callVirtual.getMethod();
                    if (methodRef instanceof SingleMethodRef ref) {
                        Method actual = constantPool.dereference(ref);
                        resolveRValue(callVirtual.getObj());
                        for (int i = 0; i < callVirtual.getArgs().size(); i++) {
                            var rValue = callVirtual.getArgs().get(i);
                            resolveRValue(rValue, actual.parameters()[i].type());
                        }

                        var mtd = Utils.methodTypeDescOf(actual);
                        if (actual.owner().isInterface())
                            codeBuilder.invokeinterface(Utils.classDescOf(actual.owner()), actual.name(), mtd);
                        else
                            codeBuilder.invokevirtual(Utils.classDescOf(actual.owner()), actual.name(), mtd);
                        store(callVirtual.getTarget(), actual.returnType());
                    } else {
                        throw new UnsupportedOperationException("Method group references not yet supported");
                    }
                }
                case InvokeConstructor invokeConstructor -> {
                    var objType = resolveRValue(invokeConstructor.getObj());
                    var argTypes = invokeConstructor.getArgs().stream().map(this::resolveRValue).toList();

                    var constructorMethod = objType.findMethod("<init>", argTypes.toArray(Type[]::new)).orElseThrow();
                    codeBuilder.invokespecial(Utils.classDescOf(objType), "<init>", Utils.methodTypeDescOf(constructorMethod));
                }
                case Closure closure -> {
                    var methodRef = closure.getFunc();
                    Method method = constantPool.dereference(((SingleMethodRef) methodRef));

//                    if (!method.isStatic())
//                        resolveRValue(Reference.This.INSTANCE);
                    loadMethodRef(method);

                    // Closures will be implemented later
//                    throw new UnsupportedOperationException("Closures not yet supported");
                }
                case New new_ -> {
                    Type type = constantPool.dereference(new_.getClassRef());
                    codeBuilder.new_(Utils.classDescOf(type));
                    store(new_.getTarget(), type);
                }
                case NewArray newArray -> {
                    Type elementType = constantPool.dereference(newArray.getElementType());
                    resolveRValue(newArray.getSizeRef());
                    resolveNewArray(elementType);
                    store(newArray.getTarget(), elementType.asArray(1));
                }
                case GetField getField -> {
                    resolveRValue(getField.getObj());
                    Field field = constantPool.dereference(getField.getField());
                    if (field.owner().isArray())
                        codeBuilder.arraylength();
                    else
                        codeBuilder.getfield(Utils.classDescOf(field.owner()), field.name(), Utils.classDescOf(field.type()));
                    store(getField.getTarget(), field.type());
                }
                case PutField putField -> {
                    resolveRValue(putField.getObj());
                    resolveRValue(putField.getValue());
                    Field field = constantPool.dereference(putField.getField());
                    codeBuilder.putfield(Utils.classDescOf(field.owner()), field.name(), Utils.classDescOf(field.type()));
                }
                case GetMember getMember -> {
                    // Member access will be implemented later
                    throw new UnsupportedOperationException("GetMember not yet supported");
                }
                case GetMethod getMethod -> {
                    // Method reference will be implemented later
                    throw new UnsupportedOperationException("GetMethod not yet supported");
                }
                case GetMethodStatic getMethodStatic -> {
                    // Static method reference will be implemented later
                    throw new UnsupportedOperationException("GetMethodStatic not yet supported");
                }
                case GetStatic getStatic -> {
                    Field field = constantPool.dereference(getStatic.getField());
                    codeBuilder.getstatic(Utils.classDescOf(field.owner()), field.name(), Utils.classDescOf(field.type()));
                    store(getStatic.getTarget(), field.type());
                }
                case ArrayLoad arrayLoad -> {
                    Type arrayType = resolveRValue(arrayLoad.getArray());
                    resolveRValue(arrayLoad.getIndex());
                    resolveLoadArray(arrayType);
                    Type elementType = getArrayElementType(arrayType);
                    store(arrayLoad.getTarget(), elementType);
                }
                case ArrayStore arrayStore -> {
                    Type arrayType = resolveRValue(arrayStore.getArray());
                    resolveRValue(arrayStore.getIndex());
                    resolveRValue(arrayStore.getValue());
                    resolveStoreArray(arrayType);
                }
                case Cast cast -> {
                    var objType = resolveRValue(cast.getRef());
                    Type targetType = constantPool.dereference(cast.getType());
                    if (objType.isPrimitive() && targetType.isPrimitive()) {
                        codeBuilder.with(ConvertInstruction.of(objType.typeKind(), targetType.typeKind()));
                    } else if (!objType.isPrimitive() && !targetType.isPrimitive()) {
                        codeBuilder.checkcast(Utils.classDescOf(targetType));
                    } else {
                        throw new IllegalStateException("Cannot cast between primitive and reference types");
                    }
                    store(cast.getTarget(), targetType);
                }
                case InstanceOf instanceOf -> {
                    var objType = resolveRValue(instanceOf.getRef());
                    Type type = constantPool.dereference(instanceOf.getType());
                    if (!type.isPrimitive()) {
                        codeBuilder.instanceOf(Utils.classDescOf(type));
                    } else {
                        // Handle primitive types by boxing them
                        if (AttributeExtensionsKt.contains(type.attributes(), ExtensionAttribute.class)) {
                            type = Objects.requireNonNull(
                                    AttributeExtensionsKt.get(type.attributes(), ExtensionAttribute.class)
                            ).getType();
                        }
                        if (type instanceof PrimitiveType primitiveType) {
                            type = primitiveType.boxed();
                        }
                        codeBuilder.instanceOf(Utils.classDescOf(type));
                    }
                    store(instanceOf.getTarget(), Types.BOOLEAN);
                }
                case TypeOf typeOf -> {
                    resolveRValue(typeOf.getRef());
                    codeBuilder.invokevirtual(ConstantDescs.CD_Object, "getClass", MethodTypeDesc.of(ConstantDescs.CD_Class));
                    store(typeOf.getTarget(), Types.OBJECT);
                }
                case LabelInst labelInst -> {
                    codeBuilder.labelBinding(getLabel(labelInst.getLabel().getName()));
                }
                case Switch switch_ -> {
                    // Switch statements will be implemented later
                    throw new UnsupportedOperationException("Switch not yet supported");
                }
                case Phi phi -> {
                    // Phi nodes will be handled during SSA transformation
                    throw new UnsupportedOperationException("Phi nodes not yet supported");
                }
                case Nop nop -> {
                    codeBuilder.nop();
                }
                default -> throw new IllegalStateException("Unexpected instruction: " + inst);
            }
        }
    }

    private void loadMethodRef(Method method) {
    // 1. Это тип интерфейса (например, aurum.lang.Fn0)
        ClassDesc interfaceDesc = Utils.classDescOf(MutableModelsKt.getType(method));

        // 2. Это тип, который возвращает ИНДИ (объект интерфейса)
        MethodTypeDesc indyReturnType = MethodTypeDesc.of(interfaceDesc);

        // 3. Это сигнатура метода ВНУТРИ интерфейса и вашего целевого метода
        // Если метод Fn0.invoke() ничего не принимает и не возвращает, это ()V
        MethodTypeDesc methodSig = Utils.methodTypeDescOf(method);

        var bsm = MethodHandleDesc.ofMethod(
                DirectMethodHandleDesc.Kind.STATIC,
                ClassDesc.ofDescriptor(LambdaMetafactory.class.descriptorString()),
                "metafactory",
                MethodTypeDesc.of(ConstantDescs.CD_CallSite,
                        ConstantDescs.CD_MethodHandles_Lookup, ConstantDescs.CD_String, ConstantDescs.CD_MethodType,
                        ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType)
        );

        DirectMethodHandleDesc target = MethodHandleDesc.ofMethod(
                method.isStatic() ? DirectMethodHandleDesc.Kind.STATIC : DirectMethodHandleDesc.Kind.VIRTUAL, // Используйте VIRTUAL для обычных методов
                Utils.classDescOf(method.owner()),
                method.name(),
                methodSig
        );

        DynamicCallSiteDesc callSite = DynamicCallSiteDesc.of(
                bsm,
                "invoke",        // Имя метода в интерфейсе Fn0
                indyReturnType,  // Возвращаемый тип для инструкции indy: ()LFn0;
                methodSig,       // samMethodType: ()V (если метод void)
                target,          // Ссылка на реализацию
                methodSig        // instantiatedMethodType: ()V
        );

        codeBuilder.invokedynamic(callSite);
    }

    private void generateBootstrapMethod(Method lambdaDelegate) {

    }

    private Type getArrayElementType(Type arrayType) {
        if (arrayType instanceof ArrayType<?> array) {
            return array.componentType();
        }
        throw new IllegalStateException("Expected array type, got: " + arrayType);
    }

    private void resolveLoadArray(Type type) {
        if (!(type instanceof ArrayType<?> arrayType)) {
            throw new IllegalStateException("Expected array type, got: " + type);
        }

        codeBuilder.arrayLoad(arrayType.componentType().typeKind());
    }

    private void resolveStoreArray(Type type) {
        if (!(type instanceof ArrayType<?> arrayType)) {
            throw new IllegalStateException("Expected array type, got: " + type);
        }

        codeBuilder.arrayStore(arrayType.componentType().typeKind());
    }

    private void resolveNewArray(Type elementType) {
        if (elementType.isPrimitive()) {
            codeBuilder.newarray(elementType.typeKind());
        } else if (elementType instanceof ArrayType<?> array) {
            codeBuilder.multianewarray(Utils.classDescOf(array), array.arrayDimensions());
        } else {
            codeBuilder.anewarray(Utils.classDescOf(elementType));
        }
    }

    private void resolveReturn(@Nullable RValue value) {
        var type = resolveRValue(value);
        if (type != Types.VOID) {
            codeBuilder.return_(type.typeKind());
        } else {
            codeBuilder.return_();
        }
    }

    private Label getLabel(String name) {
        return labels.computeIfAbsent(name, k -> codeBuilder.newLabel());
    }

    private void store(LValue target, Type type) {
        if (type == Types.VOID) {
            return;
        }

        if (target instanceof Reference ref) {
            if (ref.getName().equals("_")) {
                codeBuilder.pop();
            } else {
                Variable variable = getVariable(ref, type);
                codeBuilder.storeLocal(variable.type.typeKind(), variable.index);
            }
        }
    }

    private int castPrecedence(Type type) {
        if (type.equals(Types.BYTE)) {
            return 1;
        } else if (type.equals(Types.SHORT)) {
            return 2;
        } else if (type.equals(Types.INT)) {
            return 3;
        } else if (type.equals(Types.LONG)) {
            return 4;
        } else if (type.equals(Types.FLOAT)) {
            return 5;
        } else if (type.equals(Types.DOUBLE)) {
            return 6;
        } else {
            return -1;
        }
    }

    private Pair<Type, TypeKind> convert(Type left, Type right) {
        var leftCP = castPrecedence(left);
        var rightCP = castPrecedence(right);

        if (leftCP == -1) {
            return new Pair<>(left, left.typeKind());
        }
        if (rightCP == -1) {
            return new Pair<>(right, right.typeKind());
        }

        if (leftCP > rightCP) {
            codeBuilder.with(ConvertInstruction.of(right.typeKind(), left.typeKind()));
            return new Pair<>(left, left.typeKind());
        } else if (leftCP < rightCP) {
            codeBuilder.swap();
            codeBuilder.with(ConvertInstruction.of(left.typeKind(), right.typeKind()));
            codeBuilder.swap();
            return new Pair<>(right, right.typeKind());
        }
        return new Pair<>(left, left.typeKind());
    }

    private Opcode getOperatorOpcode(Opcode baseCode, TypeKind typeKind) {
        int offset = 0;
        switch (typeKind.asLoadable()) {
            case BOOLEAN, BYTE, CHAR, SHORT, INT -> {
                // offset remains 0
            }
            case LONG -> offset = 1;
            case FLOAT -> offset = 2;
            case DOUBLE -> offset = 3;
            case REFERENCE, VOID -> {
                return null;
            }
        }

        int base = baseCode.bytecode();
        int targetBytecode = base + offset;
        
        return Arrays.stream(Opcode.values())
                .filter(op -> op.bytecode() == targetBytecode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No opcode found for base " + baseCode + " and type " + typeKind));
    }

    private Type resolveOperator(BinaryOperator operator, Type left, Type right) {
        // left and right are already on the stack
        Pair<Type, TypeKind> pair = convert(left, right);
        var type = pair.getFirst();
        var typeKind = pair.getSecond();

        switch (operator.getDefaultOpcode()) {
            case lang.aurum.ir.Opcode.Add -> {
                var opcode = getOperatorOpcode(Opcode.IADD, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Sub -> {
                var opcode = getOperatorOpcode(Opcode.ISUB, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Mul -> {
                var opcode = getOperatorOpcode(Opcode.IMUL, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Div -> {
                var opcode = getOperatorOpcode(Opcode.IDIV, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Mod -> {
                var opcode = getOperatorOpcode(Opcode.IREM, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.And -> {
                var opcode = getOperatorOpcode(Opcode.IAND, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Or -> {
                var opcode = getOperatorOpcode(Opcode.IOR, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Xor -> {
                var opcode = getOperatorOpcode(Opcode.IXOR, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Shl -> {
                var opcode = getOperatorOpcode(Opcode.ISHL, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Shr -> {
                var opcode = getOperatorOpcode(Opcode.ISHR, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.Ushr -> {
                var opcode = getOperatorOpcode(Opcode.IUSHR, typeKind);
                if (opcode != null) {
                    codeBuilder.with(OperatorInstruction.of(opcode));
                }
            }
            case lang.aurum.ir.Opcode.CmpEq -> {
                resolveComparison(Opcode.IF_ICMPEQ, Opcode.IFEQ, typeKind);
                return Types.BOOLEAN;
            }
            case lang.aurum.ir.Opcode.CmpNe -> {
                resolveComparison(Opcode.IF_ICMPNE, Opcode.IFNE, typeKind);
                return Types.BOOLEAN;
            }
            case lang.aurum.ir.Opcode.CmpLt -> {
                resolveComparison(Opcode.IF_ICMPLT, Opcode.IFLT, typeKind);
                return Types.BOOLEAN;
            }
            case lang.aurum.ir.Opcode.CmpLe -> {
                resolveComparison(Opcode.IF_ICMPLE, Opcode.IFLE, typeKind);
                return Types.BOOLEAN;
            }
            case lang.aurum.ir.Opcode.CmpGt -> {
                resolveComparison(Opcode.IF_ICMPGT, Opcode.IFGT, typeKind);
                return Types.BOOLEAN;
            }
            case lang.aurum.ir.Opcode.CmpGe -> {
                resolveComparison(Opcode.IF_ICMPGE, Opcode.IFGE, typeKind);
                return Types.BOOLEAN;
            }
            case null, default -> {
                throw new IllegalStateException("Unsupported binary operator: " + operator);
            }
        }
        
        return left;
    }

    private void resolveComparison(Opcode intCmpOpcode, Opcode singleCmpOpcode, TypeKind typeKind) {
        switch (typeKind) {
            case LONG -> {
                codeBuilder.lcmp();
                codeBuilder.ifThenElse(
                        singleCmpOpcode,
                        CodeBuilder::iconst_1,
                        CodeBuilder::iconst_0
                );
            }
            case FLOAT -> {
                codeBuilder.fcmpg();
                codeBuilder.ifThenElse(
                        singleCmpOpcode,
                        CodeBuilder::iconst_1,
                        CodeBuilder::iconst_0
                );
            }
            case DOUBLE -> {
                codeBuilder.dcmpg();
                codeBuilder.ifThenElse(
                        singleCmpOpcode,
                        CodeBuilder::iconst_1,
                        CodeBuilder::iconst_0
                );
            }
            case INT -> {
                codeBuilder.ifThenElse(
                        intCmpOpcode,
                        CodeBuilder::iconst_1,
                        CodeBuilder::iconst_0
                );
            }
            default -> {
                codeBuilder.ifThenElse(
                        singleCmpOpcode,
                        CodeBuilder::iconst_1,
                        CodeBuilder::iconst_0
                );
            }
        }
    }


    @Override
    public void accept(CodeBuilder codeBuilder) {
        this.codeBuilder = codeBuilder;
        translate();
    }

    private Type resolveRValue(@Nullable RValue rvalue) {
        return resolveRValue(rvalue, null);
    }

    private Type resolveRValue(@Nullable RValue rvalue, @Nullable Type requiredType) {
        if (rvalue == null) {
            return Types.VOID;
        }

        switch (rvalue) {
            case ConstantPoolRef constRef -> {
                Object value = constantPool.dereference(constRef);
                switch (value) {
                    case Method m -> {
//                        if (!method.isStatic())
//                            resolveRValue(Reference.This.INSTANCE);

                        loadMethodRef(m);

                        return MutableModelsKt.getType(m);
                    }
                    case Field f -> {
                        codeBuilder.getstatic(Utils.classDescOf(f.owner()), f.name(), Utils.classDescOf(f.type()));
                        return f.type();
                    }
                    case Type t -> {
                        codeBuilder.ldc(Utils.classDescOf(t));
                        return Type.ofClass(Class.class); // Class objects are of type Class
                    }
                    case Integer i -> {
                        codeBuilder.ldc(i);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Integer, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Integer, ConstantDescs.CD_int));
                            return Type.ofClass(Integer.class);
                        }
                        return Types.INT;
                    }
                    case Long l -> {
                        codeBuilder.ldc(l);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Long, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Long, ConstantDescs.CD_long));
                            return Type.ofClass(Long.class);
                        }
                        return Types.LONG;
                    }
                    case Float f -> {
                        codeBuilder.ldc(f);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Float, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Float, ConstantDescs.CD_float));
                            return Type.ofClass(Float.class);
                        }
                        return Types.FLOAT;
                    }
                    case Double d -> {
                        codeBuilder.ldc(d);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Double, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Double, ConstantDescs.CD_double));
                            return Type.ofClass(Double.class);
                        }
                        return Types.DOUBLE;
                    }
                    case Boolean b -> {
                        codeBuilder.ldc(b ? 1 : 0);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Boolean, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Boolean, ConstantDescs.CD_boolean));
                            return Type.ofClass(Boolean.class);
                        }
                        return Types.BOOLEAN;
                    }
                    case Byte b -> {
                        codeBuilder.ldc(b.intValue());
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Byte, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Byte, ConstantDescs.CD_byte));
                            return Type.ofClass(Byte.class);
                        }
                        return Types.BYTE;
                    }
                    case Short s -> {
                        codeBuilder.ldc(s.intValue());
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Short, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Short, ConstantDescs.CD_short));
                            return Type.ofClass(Short.class);
                        }
                        return Types.SHORT;
                    }
                    case Character c -> {
                        codeBuilder.ldc((int) c);
                        if (requiredType != null && !requiredType.isPrimitive()) {
                            codeBuilder.invokestatic(ConstantDescs.CD_Character, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Character, ConstantDescs.CD_char));
                            return Type.ofClass(Character.class);
                        }
                        return Types.CHAR;
                    }
                    case String s -> {
                        codeBuilder.ldc(s);
                        return Types.STRING;
                    }
                    case ConstantDesc cd -> {
                        codeBuilder.ldc(cd);
                        return Types.OBJECT;
                    }
                    default -> {
                        throw new IllegalStateException("Unsupported constant pool value type: " + value.getClass());
                    }
                }
            }

            case Reference.Super _ -> {
                Variable variable = getVariable("this");
                codeBuilder.loadLocal(variable.type.typeKind(), variable.index);
                return method.owner().superClass();
            }

            case Reference.This this_ -> {
                Variable variable = getVariable(this_);
                codeBuilder.loadLocal(variable.type.typeKind(), variable.index);
                return variable.type;
            }

            case Reference ref -> {
                Variable variable = getVariable(ref);
                codeBuilder.loadLocal(variable.type.typeKind(), variable.index);
                return variable.type;
            }
            case NullRef _ -> {
                codeBuilder.aconst_null();
                return Types.OBJECT;
            }
            default -> {
                throw new IllegalStateException("Unexpected RValue type: " + rvalue.getClass());
            }
        }
    }

    private Variable getVariable(Reference reference) {
        return getVariable(reference.getName(), Types.OBJECT);
    }

    @SuppressWarnings("SameParameterValue")
    private Variable getVariable(String reference) {
        return getVariable(reference, Types.OBJECT);
    }

    private Variable getVariable(Reference reference, Type type) {
        return getVariable(reference.getName(), type);
    }

    private Variable getVariable(final String name, Type type) {
        Optional<Variable> candidate = variables.stream()
                .filter(v -> v.name.equals(name))
                .findFirst();
        
        if (candidate.isEmpty()) {
            Variable variable = new Variable(name, type, variables.size());
            variables.add(variable);
            return variable;
        }

        return candidate.get();
    }
}
