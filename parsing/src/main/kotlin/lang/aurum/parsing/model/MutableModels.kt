package lang.aurum.parsing.model

import aurum.lang.Fn
import lang.aurum.model.*
import lang.aurum.model.impl.*
import java.lang.reflect.AccessFlag
import java.util.*

object MutableTypePool {
    private val pool1: MutableMap<Pair<String, List<TypeArgument>?>, MutableType> = mutableMapOf() // fullname to type

    fun get(
        className: String,
        pkg: String,
        superClass: Type? = Type.ofClass(Object::class.java),
        interfaces: MutableList<out Type>? = mutableListOf(),
        fields: MutableList<Field> = mutableListOf(),
        methods: MutableList<Method> = mutableListOf(),
        accessFlags: MutableList<AccessFlag> = mutableListOf(),
        attributes: MutableList<Attribute> = mutableListOf(),
        typeParameters: MutableList<TypeParameter>? = null,
        typeArguments: MutableList<TypeArgument>? = null,
        primitive: Boolean = false
    ): MutableType {
        if (pool1.containsKey("$pkg.$className" to typeArguments))
            return pool1["$pkg.$className" to typeArguments]!!

        val mutableType = MutableType(
            className,
            pkg,
            superClass,
            interfaces,
            fields,
            methods,
            accessFlags,
            attributes,
            typeParameters,
            typeArguments,
            primitive
        )
        pool1["$pkg.$className" to typeArguments] = mutableType

        return mutableType
    }
}

open class MutableType (
    val className: String,
    val pkg: String,
    var superClass: Type? = Type.ofClass(Object::class.java),
    var interfaces: MutableList<out Type>? = mutableListOf(),
    var fields: MutableList<Field> = mutableListOf(),
    var methods: MutableList<Method> = mutableListOf(),
    var accessFlags: MutableList<AccessFlag> = mutableListOf(),
    var attributes: MutableList<Attribute> = mutableListOf(),
    var typeParameters: MutableList<TypeParameter>? = mutableListOf(),
    var typeArguments: MutableList<TypeArgument>? = mutableListOf(),
    var primitive: Boolean = false
) : Type {
    constructor() : this("", "")

    override fun className(): String = className
    override fun pkg(): String = pkg
    override fun superClass(): Type? = superClass
    override fun interfaces(): Optional<Array<out Type>> = Optional.ofNullable(interfaces?.toTypedArray())
    override fun fields(): Array<out Field> = fields.toTypedArray()
    override fun methods(): Array<out Method> = methods.toTypedArray()
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()
    override fun typeParameters(): Optional<Array<out TypeParameter>> = Optional.ofNullable(typeParameters?.toTypedArray())
    override fun typeArguments(): Optional<Array<out TypeArgument>> = Optional.ofNullable(typeArguments?.toTypedArray())
    override fun isPrimitive(): Boolean = primitive

    override fun asArray(dimensions: Int): MutableType {
        if (dimensions == 0)
            return this
        return MutableArrayType(this, dimensions)
    }

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }
    override fun withTypeArguments(typeArguments: Array<out Type>): MutableType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }
}

data class ExtensionType(
    val extendedType: Type
) : MutableType(
    "extension$${extendedType.toUsageString()}",
    ""
) {
    override fun className(): String = extendedType.className()
    override fun pkg(): String = extendedType.pkg()
    override fun superClass(): Type = extendedType.superClass()
    override fun interfaces(): Optional<Array<out Type>> = extendedType.interfaces()
    override fun fields(): Array<out Field> = extendedType.fields()
    override fun methods(): Array<out Method> = extendedType.methods()
    override fun accessFlags(): Array<out AccessFlag> = extendedType.accessFlags()
    override fun attributes(): Array<out Attribute> = extendedType.attributes()
    override fun typeParameters(): Optional<Array<out TypeParameter>> = extendedType.typeParameters()
    override fun typeArguments(): Optional<Array<out TypeArgument>> = extendedType.typeArguments()
    override fun isPrimitive(): Boolean = extendedType.isPrimitive
}

data class MutableArrayType<T : Type> (
    val componentType: T,
    var arrayDimensions: Int
) : MutableType(
    componentType.className(),
    componentType.pkg(),
    componentType.superClass(),
    componentType.interfaces().orElse(arrayOf()).toMutableList(),
    componentType.fields().toMutableList(),
    componentType.methods().toMutableList(),
    componentType.accessFlags().toMutableList(),
    componentType.attributes().toMutableList(),
    componentType.typeParameters().orElse(arrayOf()).toMutableList(),
    componentType.typeArguments().orElse(arrayOf()).toMutableList()
), ArrayType<T> {
    override fun componentType(): T = componentType

    override fun arrayDimensions(): Int = arrayDimensions

    override fun asArray(dimensions: Int): MutableArrayType<T> {
        arrayDimensions += dimensions
        return this
    }

    override fun superClass(): Type? = super<MutableType>.superClass()

    override fun fields(): Array<out Field> = super<MutableType>.fields()

    override fun methods(): Array<out Method> = super<MutableType>.methods()
}

data class MutableMethod (
    var owner: Type,
    var name: String,
    var returnType: Type = Type.ofClass(Unit.javaClass),
    var parameters: MutableList<Parameter> = mutableListOf(),
    var exceptions: MutableList<Type> = mutableListOf(),
    var accessFlags: MutableList<AccessFlag> = mutableListOf(),
    var typeParameters: MutableList<TypeParameter>? = mutableListOf(),
    var typeArguments: MutableList<TypeArgument>? = mutableListOf(),
    var attributes: MutableList<Attribute> = mutableListOf()
) : Method {
    override fun owner(): Type = owner
    override fun returnType(): Type = returnType
    override fun parameters(): Array<out Parameter> = parameters.toTypedArray()
    override fun exceptions(): Array<out Type> = exceptions.toTypedArray()
    override fun name(): String = name
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()
    override fun typeParameters(): Optional<Array<out TypeParameter>> = Optional.ofNullable(typeParameters?.toTypedArray())
    override fun typeArguments(): Optional<Array<out TypeArgument>> = Optional.ofNullable(typeArguments?.toTypedArray())
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableMethod {
        return Utils.applyTypeArguments(this, owner, typeArguments).toMutable()
    }
    override fun withTypeArguments(typeArguments: Array<out Type>): MutableMethod {
        return Utils.applyTypeArguments(this, owner, typeArguments).toMutable()
    }
}

data class MutableField (
    val owner: Type,
    var name: String,
    var type: Type? = null,
    var attributes: MutableList<Attribute> = Utils.EMPTY_ATTRIBUTES.toMutableList(),
    var accessFlags: MutableList<AccessFlag> = Utils.DEFAULT_ACCESS_FLAGS.toMutableList()
) : Field {
    override fun owner(): Type = owner
    override fun name(): String = name
    override fun type(): Type = type ?: Type.ofClass(Object::class.java)
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()
}

data class MutableUnionType (
    var types: MutableList<Type>,
) : MutableType(), UnionType {
    override fun className(): String = super<UnionType>.className()
    override fun pkg(): String = super<UnionType>.pkg()
    override fun superClass(): Type = super<UnionType>.superClass()
    override fun interfaces(): Optional<Array<out Type>> = super<UnionType>.interfaces()
    override fun fields(): Array<out Field> = super<UnionType>.fields()
    override fun methods(): Array<out Method> = super<UnionType>.methods()
    override fun accessFlags(): Array<out AccessFlag> = super<UnionType>.accessFlags()
    override fun attributes(): Array<out Attribute> = super<UnionType>.attributes()
    override fun typeParameters(): Optional<Array<out TypeParameter>> = super<UnionType>.typeParameters()
    override fun typeArguments(): Optional<Array<out TypeArgument>> = super<UnionType>.typeArguments()
    override fun isPrimitive(): Boolean = super<UnionType>.isPrimitive()
    override fun types(): Array<out Type> = types.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableUnionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withTypeArguments(typeArguments: Array<out Type>): MutableUnionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }
}


data class MutableIntersectionType (
    var types: MutableList<Type>,
) : MutableType(), IntersectionType {
    override fun className(): String = super<IntersectionType>.className()
    override fun pkg(): String = super<IntersectionType>.pkg()
    override fun superClass(): Type = super<IntersectionType>.superClass()
    override fun interfaces(): Optional<Array<out Type>> = super<IntersectionType>.interfaces()
    override fun fields(): Array<out Field> = super<IntersectionType>.fields()
    override fun methods(): Array<out Method> = super<IntersectionType>.methods()
    override fun accessFlags(): Array<out AccessFlag> = super<IntersectionType>.accessFlags()
    override fun attributes(): Array<out Attribute> = super<IntersectionType>.attributes()
    override fun typeParameters(): Optional<Array<out TypeParameter>> = super<IntersectionType>.typeParameters()
    override fun typeArguments(): Optional<Array<out TypeArgument>> = super<IntersectionType>.typeArguments()
    override fun isPrimitive(): Boolean = super<IntersectionType>.isPrimitive()
    override fun types(): Array<out Type> = types.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableIntersectionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withTypeArguments(typeArguments: Array<out Type>): MutableIntersectionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }
}

fun Member.toMutable(): Member = this

fun Field.toMutable(): MutableField {
    return MutableField(
        this.owner(),
        this.name(),
        this.type(),
        this.attributes().toMutableList(),
        this.accessFlags().toMutableList()
    )
}

fun Method.toMutable(): MutableMethod {
    return MutableMethod(
        this.owner(),
        this.name(),
        this.returnType(),
        this.parameters().toMutableList(),
        this.exceptions().toMutableList(),
        this.accessFlags().toMutableList(),
        this.typeParameters().orElse(null)?.toMutableList(),
        this.typeArguments().orElse(null)?.toMutableList(),
        this.attributes().toMutableList()
    )
}

fun Type.toMutable(): MutableType {
    return MutableTypePool.get(
        this.className(),
        this.pkg(),
        this.superClass(),
        this.interfaces().orElse(null)?.toMutableList(),
        this.fields().toMutableList(),
        this.methods().toMutableList(),
        this.accessFlags().toMutableList(),
        this.attributes().toMutableList(),
        this.typeParameters().orElse(null)?.toMutableList(),
        this.typeArguments().orElse(null)?.toMutableList(),
        this.isPrimitive
    )
}

fun <T : Type> ArrayType<T>.toMutable(): MutableArrayType<T> {
    return MutableArrayType(
        this.componentType(),
        this.arrayDimensions()
    )
}

fun UnionType.toMutable(): MutableUnionType {
    return MutableUnionType(
        this.types().toMutableList()
    )
}

fun IntersectionType.toMutable(): MutableIntersectionType {
    return MutableIntersectionType(
        this.types().toMutableList()
    )
}

fun MutableField.toImmutable(): Field {
    return FieldImpl(
        this.owner,
        this.name,
        this.type,
        this.attributes.toTypedArray(),
        this.accessFlags.toTypedArray()
    )
}

fun MutableMethod.toImmutable(): Method {
    return MethodImpl(
        this.owner,
        this.name,
        this.returnType,
        this.parameters.toTypedArray(),
        this.exceptions.toTypedArray(),
        this.accessFlags.toTypedArray(),
        Optional.ofNullable(this.typeParameters?.toTypedArray()),
        Optional.ofNullable(this.typeArguments?.toTypedArray()),
        this.attributes.toTypedArray(),
    )
}

fun MutableType.toImmutable(): Type {
    if (this.isPrimitive) {
        return PrimitiveTypeImpl.entries.find { it.name.equals(this.className, ignoreCase = true) }!!
    }

    return TypeImpl(
        this.className,
        this.pkg,
        this.superClass,
        Optional.ofNullable(this.interfaces?.toTypedArray()),
        this.fields.toTypedArray(),
        this.methods.toTypedArray(),
        this.accessFlags.toTypedArray(),
        this.attributes.toTypedArray(),
        Optional.ofNullable(this.typeParameters?.toTypedArray()),
        Optional.ofNullable(this.typeArguments?.toTypedArray()),
    )
}

fun MutableUnionType.toImmutable(): UnionType {
    return UnionTypeImpl(
        this.types()
    )
}

fun MutableIntersectionType.toImmutable(): IntersectionType {
    return IntersectionTypeImpl(
        this.types()
    )
}

fun Method.getType(): Type {
    val returnType = this.returnType()
    val parameterTypes = this.parameters().map(Parameter::type).toList()

    return fnType(returnType, parameterTypes)
}

fun fnType(returnType: Type, parameterTypes: List<Type>): Type {
    val objectType = Type.ofClass(Object::class.java)
    val typeParameters = mutableListOf<TypeParameter>(
        TypeParameterImpl("R", objectType)
    )
    val typeArguments = mutableListOf<TypeArgument>(
        TypeArgumentImpl("R", returnType)
    )

    parameterTypes.forEachIndexed { i, it ->
        typeParameters += TypeParameterImpl("T$i", objectType)
        typeArguments += TypeArgumentImpl("T$i", it)
    }

    val fnType = MutableTypePool.get(
        "Fn${parameterTypes.size}",
        "aurum.lang",
        interfaces = mutableListOf(Type.ofClass(Fn::class.java)),
        typeParameters = typeParameters
    )

    fnType.methods += invokeMethod(fnType, parameterTypes.size)

    return fnType.withTypeArguments(typeArguments.toTypedArray())
}

private fun invokeMethod(owner: Type, parametersCount: Int): MutableMethod {
    val parameters = mutableListOf<Parameter>()
    for (i in 0..<parametersCount) {
        parameters += ParameterImpl(
            "arg$i",
            TemplateTypeImpl("T$i", 0),
            Utils.EMPTY_ATTRIBUTES
        )
    }

    return MutableMethod(
        owner,
        "invoke",
        returnType = TemplateTypeImpl("R", 0),
        parameters = parameters
    )
}