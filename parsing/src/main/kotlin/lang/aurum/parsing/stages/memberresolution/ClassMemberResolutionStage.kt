package lang.aurum.parsing.stages.memberresolution

import lang.aurum.model.Type
import lang.aurum.model.impl.ParameterImpl
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.MutableField
import lang.aurum.parsing.model.MutableMethod
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.stages.*

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
                        ?: Type.ofClass(Object::class.java)

                    val interfaces = superClasses?.filterNotNull()?.toMutableList()
                    interfaces?.remove(superClass)

                    type.superClass = superClass
                    type.interfaces = interfaces

                    for (member in ctx.memberDecl()) {
                        type.resolveMember(member, typeResolver)
                    }
                }
                is AurumParser.InterfaceDeclContext -> {
                    val typeParameters = genericResolver.resolveGenericParameters(ctx.genericTypeList())

                    type.typeParameters = typeParameters.toMutableList()
                }
                is AurumParser.ExtensionDeclContext -> {
                    val typeParameters = genericResolver.resolveGenericParameters(ctx.genericTypeList())

                    type.typeParameters = typeParameters.toMutableList()
                    type.superClass = typeResolver.toUnresolvedType(ctx.typeExpr())
                }
                is AurumParser.DecoratorDeclContext -> {}
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    ctx as List<AurumParser.DeclarationContext>

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
    val fieldName = member.Identifier().text
    val type: Type? = typeResolver.toUnresolvedType(member.typeExpr())

    this.fields.add(MutableField(this, fieldName, type, accessFlags = accessFlags))
}
private fun MutableType.resolveMember(member: AurumParser.UnpackDeclContext, typeResolver: TypeResolver) {
    val accessFlags = member.modifier().toAccessFlags()
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
        ?: Type.ofClass(Void.TYPE)

    method.attributes += BlockAttribute(member.block())

    return method
}
private fun MutableType.resolveMember(member: AurumParser.FuncSignContext, typeResolver: TypeResolver): MutableMethod {
    val name = member.Identifier().text
    val accessFlags = member.modifier().toAccessFlags()
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
        ?: Type.ofClass(Void.TYPE)

    return method
}

data class ClassMemberResolutionContext (
    val fileContext: FileContext,
    val typeAndCtx: Pair<Type, TypeDeclCtx<*>>,
) : AbstractParsingContext()
