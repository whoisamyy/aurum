package aurum.lang.compiler.frontend.model

import aurum.lang.attribute.ConstantPoolAttribute
import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.stages.analyzing.GeneratedClassAttribute
import aurum.lang.ir.ConstantPool
import aurum.lang.model.*
import aurum.lang.model.attribute.ExtensionAttribute
import aurum.lang.model.factory.TypeFactory.TypePool
import aurum.lang.model.impl.*
import java.lang.reflect.AccessFlag
import java.util.concurrent.ConcurrentHashMap

object MutableTypePool {
    val pool: MutableMap<Pair<String, List<TypeArgument>?>, MutableType> = mutableMapOf() // fullname to type

    fun contains(className: String, pkg: String, typeArgument: List<TypeArgument>? = listOf()): Boolean {
        return pool.containsKey("$pkg.$className" to typeArgument)
    }

    fun get(
        className: String,
        pkg: String? = null,
        superClass: Type? = Types.OBJECT,
        interfaces: MutableList<Type> = mutableListOf(),
        fields: MutableList<Field> = mutableListOf(),
        methods: MutableList<Method> = mutableListOf(),
        accessFlags: MutableList<AccessFlag> = mutableListOf(),
        attributes: MutableList<Attribute> = mutableListOf(),
        typeParameters: MutableList<TypeParameter> = mutableListOf(),
        typeArguments: MutableList<TypeArgument> = mutableListOf(),
        primitive: Boolean = false
    ): MutableType {
        val fullname = if (pkg != null) "$pkg.$className" else className
        if (pool.containsKey(fullname to typeArguments))
            return pool[fullname to typeArguments]!!

        val mutableType = MutableType(
            className,
            pkg ?: "",
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
        pool[fullname to typeArguments] = mutableType

        return mutableType
    }
}

open class MutableType (
    val className: String,
    val pkg: String,
    var superClass: Type? = Types.OBJECT,
    var interfaces: MutableList<Type> = mutableListOf(),
    var fields: MutableList<Field> = mutableListOf(),
    var methods: MutableList<Method> = mutableListOf(),
    var accessFlags: MutableList<AccessFlag> = mutableListOf(),
    var attributes: MutableList<Attribute> = mutableListOf(),
    var typeParameters: MutableList<TypeParameter> = mutableListOf(),
    var typeArguments: MutableList<TypeArgument> = mutableListOf(),
    var primitive: Boolean = false
) : Type {
    constructor() : this("", "")

//    init {
//        this.withTypeArguments(this.typeArguments.toTypedArray())
//    }

    override fun className(): String = className
    override fun pkg(): String = pkg
    override fun superClass(): Type? = superClass
    override fun interfaces(): Array<out Type> = interfaces.toTypedArray()
    override fun fields(): Array<out Field> {
        val type = this // .withTypeArguments(typeArguments?.toTypedArray() ?: arrayOf())
        return (type.fields + (type.superClass?.fields()?.toMutableList() ?: mutableListOf())).toTypedArray()
    }
    override fun methods(): Array<out Method> {
        val type = this // .withTypeArguments(typeArguments?.toTypedArray() ?: arrayOf())
        return (type.methods.toTypedArray() +
                (type.superClass?.methods() ?: arrayOf()) +
                type.interfaces.map(Type::methods).flatMap(Array<Method>::toList))
            .toSet()
            .toTypedArray()
    }
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()
    override fun typeParameters(): Array<out TypeParameter> = typeParameters.toTypedArray()
    override fun typeArguments(): Array<out TypeArgument> = typeArguments.toTypedArray()
    override fun isPrimitive(): Boolean = primitive

    override fun asArray(dimensions: Int): MutableType {
        if (dimensions == 0)
            return this
        return MutableArrayType(this, dimensions)
    }

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): Type {
        return Utils.applyTypeArguments(this, typeArguments)
    }
    override fun withTypeArguments(typeArguments: Array<out Type>): Type {
        return Utils.applyTypeArguments(this, typeArguments)
    }

    override fun withDefaultTypeArguments(): Type {
        return this.withTypeArguments(
            this.typeParameters
                .map { it.bound() }
                .toTypedArray()
        )
    }

    fun applyTypeArguments(vararg typeArguments: TypeArgument) {
        val newType = Utils.applyTypeArguments(this, typeArguments)

        copyFrom(newType)
    }

    fun applyTypeArguments(vararg types: Type) {
        val newType = Utils.applyTypeArguments(this, types)

        copyFrom(newType)
    }

    private fun copyFrom(newType: Type) {
        this.superClass = newType.superClass()
        this.interfaces = newType.interfaces().toMutableList()
        this.fields = newType.fields().toMutableList()
        this.methods = newType.methods().toMutableList()
        this.accessFlags = newType.accessFlags().toMutableList()
        this.attributes = newType.attributes().toMutableList()
        this.typeParameters = newType.typeParameters().toMutableList()
        this.typeArguments = newType.typeArguments().toMutableList()
    }

    override fun toString(): String {
        return toUsageString()
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
    override fun superClass(): Type? = extendedType.superClass()
    override fun interfaces(): Array<out Type> = extendedType.interfaces()
    override fun fields(): Array<out Field> = extendedType.fields()
    override fun methods(): Array<out Method> = extendedType.methods()
    override fun accessFlags(): Array<out AccessFlag> = extendedType.accessFlags()
    override fun attributes(): Array<out Attribute> = extendedType.attributes()
    override fun typeParameters(): Array<out TypeParameter> = extendedType.typeParameters()
    override fun typeArguments(): Array<out TypeArgument> = extendedType.typeArguments()
    override fun isPrimitive(): Boolean = extendedType.isPrimitive
    override fun isPlainType(): Boolean = extendedType.isPlainType
    override fun isArray(): Boolean = extendedType.isArray

    override fun toString(): String {
        return toUsageString()
    }
}

data class MutableArrayType<T : Type> (
    val componentType: T,
    var arrayDimensions: Int
) : MutableType(
    componentType.className(),
    componentType.pkg(),
    componentType.superClass(),
    componentType.interfaces().toMutableList(),
    componentType.fields().toMutableList(),
    componentType.methods().toMutableList(),
    componentType.accessFlags().toMutableList(),
    componentType.attributes().toMutableList(),
    componentType.typeParameters().toMutableList(),
    componentType.typeArguments().toMutableList()
), ArrayType<T> {
    companion object {
        private val arrayTypeFields: MutableMap<MutableArrayType<*>, Array<Field>> = ConcurrentHashMap()
        private val arrayTypeMethods: MutableMap<MutableArrayType<*>, Array<Method>> = ConcurrentHashMap()
    }

    override fun fields(): Array<out Field> {
        return arrayTypeFields.computeIfAbsent(
            this
        ) { t ->
            arrayOf(
                FieldImpl(
                    t,
                    "length",
                    Types.INT,
                    Utils.EMPTY_ATTRIBUTES,
                    Utils.DEFAULT_ACCESS_FLAGS
                )
            )
        }
    }

    override fun methods(): Array<Method> {
        return arrayTypeMethods.computeIfAbsent(
            this
        ) { t ->
            val methods = mutableListOf(*Types.OBJECT.methods())
            methods.removeIf { m -> m.name() == "<init>" }
            methods.add(
                MethodImpl(
                    t,
                    "<init>",
                    t,
                    Utils.DEFAULT_ARRAY_INIT_PARAMETERS,
                    Utils.EMPTY_TYPES,
                    Utils.DEFAULT_ACCESS_FLAGS,
                    Utils.EMPTY_TYPE_PARAMETERS,
                    Utils.EMPTY_TYPE_ARGUMENTS,
                    Utils.EMPTY_ATTRIBUTES
                )
            )
            methods.toTypedArray()
        }
    }

    override fun componentType(): T = componentType

    override fun arrayDimensions(): Int = arrayDimensions

    override fun asArray(dimensions: Int): MutableArrayType<T> {
        arrayDimensions += dimensions
        return this
    }

    override fun superClass(): Type? = super<MutableType>.superClass()

    override fun toString(): String {
        return toUsageString()
    }
}

open class MutableMethod (
    var owner: Type,
    var name: String,
    var returnType: Type = Types.VOID,
    var parameters: MutableList<Parameter> = mutableListOf(),
    var exceptions: MutableList<Type> = mutableListOf(),
    var accessFlags: MutableList<AccessFlag> = mutableListOf(),
    var typeParameters: MutableList<TypeParameter> = mutableListOf(),
    var typeArguments: MutableList<TypeArgument> = mutableListOf(),
    var attributes: MutableList<Attribute> = mutableListOf()
) : Method {
    override fun owner(): Type = owner
    override fun returnType(): Type = returnType
    override fun parameters(): Array<out Parameter> = parameters.toTypedArray()
    override fun exceptions(): Array<out Type> = exceptions.toTypedArray()
    override fun name(): String = name
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()
    override fun typeParameters(): Array<out TypeParameter> = typeParameters.toTypedArray()
    override fun typeArguments(): Array<out TypeArgument> = typeArguments.toTypedArray()
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): Method {
        return Utils.applyTypeArguments(this, owner, typeArguments)
    }
    override fun withTypeArguments(typeArguments: Array<out Type>): Method {
        return Utils.applyTypeArguments(this, owner, typeArguments)
    }

    fun applyTypeArguments(vararg typeArguments: TypeArgument) {
        val newMethod = this.withTypeArguments(typeArguments)

        copyFrom(newMethod)
    }

    fun applyTypeArguments(vararg types: Type) {
        val newMethod = this.withTypeArguments(types)

        copyFrom(newMethod)
    }

    private fun copyFrom(newMethod: Method) {
        this.owner = newMethod.owner()
        this.name = newMethod.name()
        this.returnType = newMethod.returnType()
        this.parameters = newMethod.parameters().toMutableList()
        this.exceptions = newMethod.exceptions().toMutableList()
        this.accessFlags = newMethod.accessFlags().toMutableList()
        this.typeParameters = newMethod.typeParameters().toMutableList()
        this.typeArguments = newMethod.typeArguments().toMutableList()
        this.attributes = newMethod.attributes().toMutableList()
    }

    override fun toString(): String {
        return "${returnType.toUsageString()} ${owner.toUsageString()}.$name(${
            parameters.map(Parameter::type).joinToString(", ", transform = Type::toUsageString)
        })"
    }
}

open class MutableField (
    val owner: Type,
    var name: String,
    var type: Type = Types.OBJECT,
    var attributes: MutableList<Attribute> = mutableListOf(),
    var accessFlags: MutableList<AccessFlag> = mutableListOf()
) : Field {
    override fun owner(): Type = owner
    override fun name(): String = name
    override fun type(): Type = type
    override fun attributes(): Array<out Attribute> = attributes.toTypedArray()
    override fun accessFlags(): Array<out AccessFlag> = accessFlags.toTypedArray()

    override fun toString(): String {
        return buildString {
            if (accessFlags.isNotEmpty())
                append(accessFlags.joinToString(" ", postfix = " ") { it.name.lowercase() })

            append(type.toUsageString())
            append(" ").append(name)
        }
    }
}

data class MutableUnionType (
    var types: MutableList<Type>,
) : MutableType(), UnionType {
    override fun className(): String = super<UnionType>.className()
    override fun pkg(): String = super<UnionType>.pkg()
    override fun superClass(): Type = super<UnionType>.superClass()
    override fun interfaces(): Array<out Type> = super<UnionType>.interfaces()
    override fun fields(): Array<out Field> = super<UnionType>.fields()
    override fun methods(): Array<out Method> = super<UnionType>.methods()
    override fun accessFlags(): Array<out AccessFlag> = super<UnionType>.accessFlags()
    override fun attributes(): Array<out Attribute> = super<UnionType>.attributes()
    override fun typeParameters(): Array<out TypeParameter> = super<UnionType>.typeParameters()
    override fun typeArguments(): Array<out TypeArgument> = super<UnionType>.typeArguments()
    override fun isPrimitive(): Boolean = super<UnionType>.isPrimitive()
    override fun types(): Array<out Type> = types.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableUnionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withTypeArguments(typeArguments: Array<out Type>): MutableUnionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withDefaultTypeArguments(): UnionType {
        return this // TODO
    }

    override fun toString(): String {
        return toUsageString()
    }
}


data class MutableIntersectionType (
    var types: MutableList<Type>,
) : MutableType(), IntersectionType {
    override fun className(): String = super<IntersectionType>.className()
    override fun pkg(): String = super<IntersectionType>.pkg()
    override fun superClass(): Type = super<IntersectionType>.superClass()
    override fun interfaces(): Array<out Type> = super<IntersectionType>.interfaces()
    override fun fields(): Array<out Field> = super<IntersectionType>.fields()
    override fun methods(): Array<out Method> = super<IntersectionType>.methods()
    override fun accessFlags(): Array<out AccessFlag> = super<IntersectionType>.accessFlags()
    override fun attributes(): Array<out Attribute> = super<IntersectionType>.attributes()
    override fun typeParameters(): Array<out TypeParameter> = super<IntersectionType>.typeParameters()
    override fun typeArguments(): Array<out TypeArgument> = super<IntersectionType>.typeArguments()
    override fun isPrimitive(): Boolean = super<IntersectionType>.isPrimitive()
    override fun types(): Array<out Type> = types.toTypedArray()

    override fun withTypeArguments(typeArguments: Array<out TypeArgument>): MutableIntersectionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withTypeArguments(typeArguments: Array<out Type>): MutableIntersectionType {
        return Utils.applyTypeArguments(this, typeArguments).toMutable()
    }

    override fun withDefaultTypeArguments(): IntersectionType {
        return this // TODO
    }

    override fun toString(): String {
        return toUsageString()
    }
}

data class MutablePackage (
    val name: String,
    val parent: Package? = null,
    val publicTypes: MutableSet<Type> = mutableSetOf(),
    val privateTypes: MutableSet<Type> = mutableSetOf(),
    val publicMembers: MutableSet<Member> = mutableSetOf(),
    val privateMembers: MutableSet<Member> = mutableSetOf(),
    val publicPackages: MutableSet<Package> = mutableSetOf(),
    val privatePackages: MutableSet<Package> = mutableSetOf(),
) : Package {
    override fun name(): String = name
    override fun parent(): Package? = parent
    override fun publicTypes(): Array<out Type> = publicTypes.toTypedArray()
    override fun privateTypes(): Array<out Type> = privateTypes.toTypedArray()
    override fun publicMembers(): Array<out Member> = publicMembers.toTypedArray()
    override fun privateMembers(): Array<out Member> = privateMembers.toTypedArray()
    override fun publicPackages(): Array<out Package> = publicPackages.toTypedArray()
    override fun privatePackages(): Array<out Package> = privatePackages.toTypedArray()
}

fun Package.toMutable(): MutablePackage {
    return MutablePackage(
            this.name(),
            this.parent(),
            this.publicTypes().toMutableSet(),
            this.privateTypes().toMutableSet(),
            this.publicMembers().toMutableSet(),
            this.privateMembers().toMutableSet(),
            this.publicPackages().toMutableSet(),
            this.privatePackages().toMutableSet()
    )
}

fun MutablePackage.toImmutable(): Package {
    return Package.of(
        this.name(),
        this.parent(),
        this.publicTypes(),
        this.privateTypes(),
        this.publicMembers(),
        this.privateMembers(),
        this.publicPackages(),
        this.privatePackages()
    )
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
        this.typeParameters().toMutableList(),
        this.typeArguments().toMutableList(),
        this.attributes().toMutableList()
    )
}

fun Type.toMutable(): MutableType {
    return MutableTypePool.get(
        this.className(),
        this.pkg(),
        this.superClass(),
        this.interfaces().toMutableList(),
        this.fields().toMutableList(),
        this.methods().toMutableList(),
        this.accessFlags().toMutableList(),
        this.attributes().toMutableList(),
        this.typeParameters().toMutableList(),
        this.typeArguments().toMutableList(),
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
        this.typeParameters.toTypedArray(),
        this.typeArguments.toTypedArray(),
        this.attributes.toTypedArray(),
    )
}

fun MutableType.toImmutable(): Type {
    if (this.isPrimitive && !this.attributes.contains<ExtensionAttribute>()) {
        return PrimitiveTypeImpl.entries.find { it.name.equals(this.className, ignoreCase = true) }!!
    }

    return TypePool.getOrCompute(
        this.className,
        this.pkg,
        this.superClass,
        this.interfaces.toTypedArray(),
        this.fields.toTypedArray(),
        this.methods.toTypedArray(),
        this.accessFlags.toTypedArray(),
        this.attributes.toTypedArray(),
        this.typeParameters.toTypedArray(),
        this.typeArguments.toTypedArray(),
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
    val objectType = Types.OBJECT
    val typeParameters: MutableList<TypeParameter> = if (returnType != Types.VOID) mutableListOf(
        TypeParameterImpl.of("R", objectType)
    ) else mutableListOf()
    val typeArguments: MutableList<TypeArgument> = if (returnType != Types.VOID) mutableListOf(
        TypeArgumentImpl.of("R", returnType)
    ) else mutableListOf()

    parameterTypes.forEachIndexed { i, it ->
        typeParameters += TypeParameterImpl.of("T$i", objectType)
        typeArguments += TypeArgumentImpl.of("T$i", it)
    }

    val className = "Fn${if (returnType == Types.VOID) "V" else ""}${parameterTypes.size.takeIf { it > 0 } ?: ""}"
    val pkg = "aurum.lang"
    val getter = {
        MutableTypePool.get(
            className,
            pkg,
            accessFlags = mutableListOf(AccessFlag.INTERFACE, AccessFlag.ABSTRACT, AccessFlag.PUBLIC),
//        interfaces = mutableListOf(
//            if (returnType != Types.VOID)
//                Type.ofClass(Fn::class.java)
//            else
//                Type.ofClass(FnV::class.java)
//        ),
            typeParameters = typeParameters,
            attributes = mutableListOf(GeneratedClassAttribute, ConstantPoolAttribute(ConstantPool()))
        )
    }

    if (MutableTypePool.contains(className, pkg)) {
        return getter()
    }

    val fnType = getter()

    val invokeMethod = createInvokeMethod(fnType, parameterTypes.size)
    if (!fnType.methods.contains(invokeMethod))
        fnType.methods += invokeMethod

    return fnType.withTypeArguments(typeArguments.toTypedArray())
}

private fun createInvokeMethod(owner: Type, parametersCount: Int): MutableMethod {
    val parameters = mutableListOf<Parameter>()
    for (i in 0..<parametersCount) {
        parameters += ParameterImpl(
            "arg$i",
            TemplateTypeImpl.of("T$i"),
            Utils.EMPTY_ATTRIBUTES
        )
    }

    return MutableMethod(
        owner,
        "invoke",
        returnType = if (owner.className().startsWith("FnV"))
            Types.VOID
        else
            TemplateTypeImpl.of("R"),
        parameters = parameters,
        accessFlags = mutableListOf(AccessFlag.PUBLIC, AccessFlag.ABSTRACT)
    )
}