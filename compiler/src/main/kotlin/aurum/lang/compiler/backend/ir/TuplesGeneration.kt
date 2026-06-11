package aurum.lang.compiler.backend.ir

import aurum.lang.Pair
import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.frontend.model.MutableField
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableTypePool
import aurum.lang.compiler.frontend.stages.analyzing.GeneratedClassAttribute
import aurum.lang.ir.*
import aurum.lang.model.*
import java.lang.reflect.AccessFlag

fun generateTupleOf(vararg types: Type): Type {
    val elementCount = types.size

    if (elementCount == 1) {
        return types[0]
    } else if (elementCount == 2) {
        return Type.ofClass(Pair::class.java).withTypeArguments(*types)
    }

    val typeParams = types.mapIndexed { i, _ ->
        TypeParameter.of("T${i+1}")
    }.toMutableList()

    if (MutableTypePool.contains(
            "Tuple$elementCount",
            $$"aurum.lang$generated",
        types.mapIndexed { i, it -> TypeArgument.of("T${i+1}", it) }
    )) {
        return MutableTypePool.get(
            "Tuple$elementCount",
            $$"aurum.lang$generated",
            typeArguments = types.mapIndexed { i, it -> TypeArgument.of("T${i+1}", it) }.toMutableList()
        )
    } else if (MutableTypePool.contains(
            "Tuple$elementCount",
            $$"aurum.lang$generated")
        ) {
        return MutableTypePool.get(
            "Tuple$elementCount",
            $$"aurum.lang$generated"
        ).withTypeArguments(types)
    }

    val type = MutableTypePool.get(
        "Tuple$elementCount",
        $$"aurum.lang$generated",
        typeParameters = typeParams
    )

    val fields = typeParams.mapIndexed { i, it ->
        MutableField(
            type,
            "component${i + 1}",
            it.toTemplate(),
            accessFlags = mutableListOf(AccessFlag.FINAL, AccessFlag.PUBLIC)
        )
    }
    type.fields += fields

    type.superClass = Types.OBJECT

    val constructor = MutableMethod(
        type,
        "<init>",
        parameters = typeParams
            .mapIndexed { i, it -> Parameter.of("t${i + 1}", it.toTemplate()) }
            .toMutableList(),
        accessFlags = mutableListOf(AccessFlag.FINAL, AccessFlag.PUBLIC)
    )
    type.methods += constructor

    val cp = ConstantPool()
    val code = mutableListOf<Instruction>()

    code += CallMethod(
        Reference.Empty,
        Reference.Super,
        cp.getReference(Types.OBJECT.findMethod("<init>").get()),
        listOf()
    )

    code += fields.mapIndexed { i, it ->
        PutField(Reference.This, cp.getReference(it), Reference.Named("t${i+1}"))
    }
    code += Return()

    constructor.attributes += CodeAttribute(code)
    type.attributes += ConstantPoolAttribute(cp)
    type.attributes += GeneratedClassAttribute

    return type.withTypeArguments(types)
}