package lang.aurum.parsing.stages.memberresolution

import lang.aurum.attribute.OperatorAttribute
import lang.aurum.ir.BinaryOperator
import lang.aurum.ir.CustomOperator
import lang.aurum.ir.Operator
import lang.aurum.ir.UnaryOperator
import lang.aurum.model.Type
import lang.aurum.model.Types
import lang.aurum.model.impl.ParameterImpl
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.attribute.*
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.stages.*
import java.lang.reflect.AccessFlag

class ClassMemberResolutionStage(parsingContext: ParsingContext) : ParsingStage(parsingContext) {
    override fun execute(file: FileContext) {
        file.classes.forEach { pair ->
            val type = (pair.key as? MutableType) ?: return@forEach
            val ctx = pair.value.ctx

            val genericResolver = GenericResolver(file)
            val typeResolver = TypeResolver(file)
            typeResolver.genericResolver = genericResolver
            genericResolver.typeResolver = typeResolver

            when (ctx) {
                is AurumParser.ClassDeclContext -> {
                    val typeParameters = genericResolver.resolveGenericParameters(ctx.genericTypeList())

                    type.typeParameters = typeParameters.toMutableList()
                    val superClasses = ctx.typeExprList()?.typeExpr()?.map(typeResolver::toUnresolvedType)
                    val superClass = superClasses
                        ?.find { it?.isInterface == false }
                        ?: Types.OBJECT

                    val interfaces = superClasses?.filterNotNull()?.toMutableList()
                    interfaces?.remove(superClass)

                    type.superClass = superClass
                    type.interfaces = interfaces

                    val constructorParams = ctx.defaultConstructorParamList()?.defaultConstructorParam()?.map {
                        val name = it.Identifier().text
                        val vType = typeResolver.toUnresolvedType(it.typeExpr())

                        if (it.varDefKW() != null) {
                            type.fields += MutableField(
                                type,
                                name,
                                vType,
                                accessFlags = it.modifier().toAccessFlags(),
                                attributes = mutableListOf(PrimaryConstructorAttribute)
                            )
                        }

                        ParameterImpl(name, vType, arrayOf(PrimaryConstructorAttribute))
                    }

                    constructorParams?.let {
                        val constructor = MutableMethod(
                            type,
                            "<init>",
                            parameters = it.toMutableList(),
                            attributes = mutableListOf(PrimaryConstructorAttribute)
                        )
                        type.methods += constructor
                    }

                    for (member in ctx.memberDecl()) {
                        type.resolveMember(member, typeResolver)
                    }
                }
                is AurumParser.InterfaceDeclContext -> {
                    val typeParameters = genericResolver.resolveGenericParameters(ctx.genericTypeList())

                    type.typeParameters = typeParameters.toMutableList()
                    val superClasses = ctx.typeExprList()?.typeExpr()?.map(typeResolver::toUnresolvedType)
                    val superClass = superClasses
                        ?.find { it?.isInterface == false }
                        ?: Types.OBJECT

                    val interfaces = superClasses?.filterNotNull()?.toMutableList()
                    interfaces?.remove(superClass)

                    type.superClass = superClass
                    type.interfaces = interfaces

                    for (member in ctx.funcSign()) {
                        type.methods += type.resolveMember(member, typeResolver)
                    }
                }
                is AurumParser.ExtensionDeclContext -> {
                    val typeParameters = genericResolver.resolveGenericParameters(ctx.genericTypeList())

                    type.typeParameters = typeParameters.toMutableList()
                    val superClass = typeResolver.toUnresolvedType(ctx.typeExpr())
                    type.superClass = superClass

                    if (type.attributes.contains<ExtensionAttributeImpl>()) {
                        val extensionAttr = type.attributes.get<ExtensionAttributeImpl>()!!
                        type.superClass = typeResolver.toUnresolvedType(extensionAttr.typeCtx)!!
                        extensionAttr.type = type.superClass!!
                    }

                    type.primitive = superClass!!.isPrimitive


                    for (extMember in ctx.extensionMember()) {
                        when (val member = extMember.getChild(0)) {
                            is AurumParser.FuncDeclContext -> type.methods += type.resolveMember(member, typeResolver)
                            is AurumParser.OperatorDeclContext -> type.methods += type.resolveMember(member, typeResolver)
                            is AurumParser.VarDeclContext -> type.resolveMember(member, typeResolver)
                        }
                    }

                    val key = file.importMap.typeMap.filter { it.value == type.superClass }.keys.find { true }
                    if (key != null)
                    file.importMap[key] = type

                    for (member in type.members()) {
                        (member as? MutableMethod)?.let {
                            it.accessFlags += AccessFlag.STATIC
                            it.parameters.addFirst(ParameterImpl("this", type.superClass))

                        }
                        (member as? MutableField)?.let { it.accessFlags += AccessFlag.STATIC }
                    }
                }
                is AurumParser.DecoratorDeclContext -> {}
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    ctx as List<AurumParser.DeclarationContext>
                    type.attributes += FileClassAttribute

                    ctx.forEach {
                        when {
                            it.varDecl() != null -> type.resolveMember(it.varDecl(), typeResolver)
                            it.funcDecl() != null -> type.methods += type.resolveMember(it.funcDecl(), typeResolver)
                            it.operatorDecl() != null -> type.methods += type.resolveMember(it.operatorDecl(), typeResolver)
                        }
                    }
                }
            }
        }
    }

    private fun MutableType.resolveMember(member: AurumParser.MemberDeclContext, typeResolver: TypeResolver) {
        val varDecl = member.varDecl()
        val funcDecl = member.funcDecl()
        val constructorDecl = member.constructorDecl()
        val operatorDecl = member.operatorDecl()
        val funcSign = member.funcSign()

        when {
             varDecl != null -> {
                 this.resolveMember(varDecl, typeResolver)
             }
             funcDecl != null -> {
                 this.methods += this.resolveMember(funcDecl, typeResolver)
             }
             constructorDecl != null -> {
                 this.methods += this.resolveMember(constructorDecl, typeResolver)
             }
             operatorDecl != null -> {
                 this.methods += this.resolveMember(operatorDecl, typeResolver)
             }
             funcSign != null -> {
                 this.methods += this.resolveMember(funcSign, typeResolver)
             }
        }
    }
}

private fun MutableType.resolveMember(member: AurumParser.VarDeclContext, typeResolver: TypeResolver) {
    when (member) {
        is AurumParser.SingleDeclContext -> resolveMember(member, typeResolver)
        is AurumParser.UnpackDeclContext -> resolveMember(member, typeResolver)
        is AurumParser.MultiDeclContext -> resolveMember(member, typeResolver)
    }
}
private fun MutableType.resolveMember(member: AurumParser.SingleDeclContext, typeResolver: TypeResolver) {
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val fieldName = member.Identifier().text
    val type: Type? = typeResolver.toUnresolvedType(member.typeExpr())

    this.fields.add(MutableField(this, fieldName, type, accessFlags = accessFlags))
}
private fun MutableType.resolveMember(member: AurumParser.UnpackDeclContext, typeResolver: TypeResolver) {
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val vars = member.varId().map { it.Identifier().text to typeResolver.toUnresolvedType(it.typeExpr()) }

    this.fields.addAll(
        vars.map { MutableField(
            this,
            it.first,
            it.second,
            accessFlags = accessFlags
        ) }
    )
}
private fun MutableType.resolveMember(member: AurumParser.MultiDeclContext, typeResolver: TypeResolver) {
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val vars = member.varIdAssignment().map {
        val varId = it.varId()
        varId.Identifier().text to typeResolver.toUnresolvedType(varId.typeExpr())
    }

    this.fields.addAll(
        vars.map { MutableField(
            this,
            it.first,
            it.second,
            accessFlags = accessFlags
        ) }
    )
}

private fun MutableType.resolveMember(member: AurumParser.FuncDeclContext, typeResolver: TypeResolver): MutableMethod {
    val method = resolveMember(member.funcSign(), typeResolver)
    method.attributes += BlockAttribute(member.block())

    return method
}
private fun MutableType.resolveMember(member: AurumParser.ConstructorDeclContext, typeResolver: TypeResolver): MutableMethod {
    val name = "<init>"
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val method = MutableMethod(
        this,
        name,
        accessFlags = accessFlags
    )

    val newTypeResolver = TypeResolver(typeResolver)
    method.typeParameters = newTypeResolver.genericResolver
        .resolveGenericParameters(member.genericTypeList()).toMutableList()

    member.paramList()?.param()?.forEach {
        method.parameters += ParameterImpl(
            it.Identifier().text,
            newTypeResolver.toUnresolvedType(it.typeExpr())
        )
    }

    method.attributes += BlockAttribute(member.block())

    return method
}
private fun MutableType.resolveMember(member: AurumParser.OperatorDeclContext, typeResolver: TypeResolver): MutableMethod {
    val name = member.OperatorSymbol().text
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val method = MutableMethod(
        this,
        name,
        accessFlags = accessFlags
    )

    val newTypeResolver = TypeResolver(typeResolver)
    method.typeParameters = newTypeResolver.genericResolver
        .resolveGenericParameters(member.genericTypeList()).toMutableList()

    member.paramList()?.param()?.forEach {
        method.parameters += ParameterImpl(
            it.Identifier().text,
            newTypeResolver.toUnresolvedType(it.typeExpr())
        )
    }

    method.returnType = newTypeResolver.toUnresolvedType(member.returnType()?.typeExpr())
        ?: Types.VOID

    val operator: Operator
    if (method.isStatic) {
        operator = when (method.parameters.size) {
            1 -> UnaryOperator.entries.find { it.symbol == method.name }
                ?: CustomOperator(method.name, 12 * 10, isBinary = false)
            2 -> BinaryOperator.entries.find { it.symbol == method.name }
                ?: CustomOperator(method.name, 12 * 10)
            else -> throw IllegalStateException("todo")
        }
    } else {
        operator = when (method.parameters.size) {
            0 -> UnaryOperator.entries.find { it.symbol == method.name }
                ?: CustomOperator(method.name, 12 * 10, isBinary = false)
            1 -> BinaryOperator.entries.find { it.symbol == method.name }
                ?: CustomOperator(method.name, 12 * 10)
            else -> throw IllegalStateException("todo")
        }
    }

    method.attributes += OperatorAttribute(operator)

    method.attributes += BlockAttribute(member.block())

    return method
}
private fun MutableType.resolveMember(member: AurumParser.FuncSignContext, typeResolver: TypeResolver): MutableMethod {
    val name = member.Identifier().text
    val accessFlags = member.modifier().toAccessFlags()
    if (this.attributes.contains<FileClassAttribute>()) {
        accessFlags += AccessFlag.STATIC
        if (accessFlags.none { it in setOf(AccessFlag.PUBLIC, AccessFlag.PRIVATE, AccessFlag.PROTECTED) })
            accessFlags += AccessFlag.PUBLIC
    }
    val method = MutableMethod(
        this,
        name,
        accessFlags = accessFlags
    )

    val newTypeResolver = TypeResolver(typeResolver)
    method.typeParameters = newTypeResolver.genericResolver
        .resolveGenericParameters(member.genericTypeList()).toMutableList()

    member.paramList()?.param()?.forEach {
        method.parameters += ParameterImpl(
            it.Identifier().text,
            newTypeResolver.toUnresolvedType(it.typeExpr())
        )
    }

    method.returnType = newTypeResolver.toUnresolvedType(member.returnType()?.typeExpr())
        ?: Types.VOID

    return method
}

data class ClassMemberResolutionContext (
    val fileContext: FileContext,
    val typeAndCtx: Pair<Type, TypeDeclCtx<*>>,
) : AbstractParsingContext()
