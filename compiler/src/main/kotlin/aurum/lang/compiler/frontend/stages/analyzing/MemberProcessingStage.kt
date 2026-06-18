package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.attribute.contains
import aurum.lang.compiler.frontend.attribute.get
import aurum.lang.compiler.frontend.model.MutableField
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.typeresolving.AbstractTypeResolver
import aurum.lang.model.Parameter
import aurum.lang.model.Type
import aurum.lang.model.TypeParameter
import aurum.lang.model.Types
import aurum.lang.model.attribute.ExtensionAttribute
import aurum.lang.model.attribute.PrimaryConstructorAttribute
import aurum.lang.model.impl.Utils
import java.lang.reflect.AccessFlag

class MemberProcessingStage : Stage() {
    val definedPackages = input<DefinedPackages>()
    val processedTypes = input<ProcessedTypes>()

    val typeResolverFactory = input<TypeResolverFactory<*>>()

    init {
        dependsOn<TypeProcessingStage>()
    }

    val packages by lazy { definedPackages.get() }
    private val availableTypes by lazy {
        processedTypes.get().groupBy(ProcessedType::file)
            .mapValues { (file, types) ->
                types.map(ProcessedType::type).toSet() + file.importedTypes() +
                        (packages.find { it.name() == file.pkg }?.types() ?: arrayOf())
            }
    }

    override fun execute() {
        processedTypes.get()
            .forEach(::processTypeMembers)

//        processedTypes.set(ProcessedTypes(processed))
    }

    fun processTypeMembers(type: ProcessedType): Type {
        return when (type.declaration) {
            is ASTNode.ClassDeclaration -> processClassMembers(type)
            is ASTNode.InterfaceDeclaration -> processInterfaceMembers(type)
            is ASTNode.ExtensionDeclaration -> processExtensionMembers(type)
            is ASTNode.DecoratorDeclaration -> processDecoratorMembers(type)
        }
    }

    fun processClassMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.ClassDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeResolver = getTypeResolver(type)

        val members = declaration.members

        processImplicitConstructor(declaration, unprocessedType, typeResolver)

        processMembers(members, unprocessedType, typeResolver)

        return unprocessedType
    }

    private fun processImplicitConstructor(
        declaration: ASTNode.ClassDeclaration,
        unprocessedType: MutableType,
        typeGetter: AbstractTypeResolver
    ) {
        if (declaration.defaultConstructorParameters == null)
            return

        val implicitConstructor = MutableMethod(
            unprocessedType,
            "<init>",
            attributes = mutableListOf(PrimaryConstructorAttribute)
        )

        declaration.defaultConstructorParameters.forEach {
            when (it) {
                is ASTNode.SingleTypedVariable -> {
                    val field = MutableField(
                        unprocessedType,
                        it.name,
                        typeGetter.getType(it.type),
                        attributes = mutableListOf(PrimaryConstructorAttribute)
                    )

                    it.defaultValue
                        ?.let { v -> DefaultValueAttribute(v) }
                        ?.let { attr -> field.attributes += attr }

                    val param = Parameter.of(
                        field.name,
                        field.type,
                        arrayOf(*field.attributes.toTypedArray())
                    )

                    implicitConstructor.parameters += param
                    unprocessedType.fields += field
                }

                is ASTNode.Parameter -> {
                    val param = Parameter.of(
                        it.name,
                        typeGetter.getType(it.type),
                        it.defaultValue
                            ?.let { v -> DefaultValueAttribute(v) }
                            ?.let { attr -> arrayOf(attr) }
                            ?: arrayOf()
                    )

                    implicitConstructor.parameters += param
                }
            }
        }

        unprocessedType.methods += implicitConstructor
    }

    fun processInterfaceMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.InterfaceDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeGetter = getTypeResolver(type)

        val members = declaration.members
        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    fun processExtensionMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.ExtensionDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeGetter = getTypeResolver(type)

        val members = declaration.members
        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    fun processDecoratorMembers(type: ProcessedType): Type {
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        // todo:
//        processParameters(declaration.parameters ?: listOf(), typeResolver)
//            .map {  }

//        val members = declaration.members
//        processMembers(members, unprocessedType, typeResolver)

        return unprocessedType
    }

    private fun processMembers(
        members: List<ASTNode.MemberDeclaration>?,
        unprocessedType: MutableType,
        typeResolver: AbstractTypeResolver
    ) {
        members?.forEach {
            when (it) {
                is ASTNode.SingleTypedVariable -> {
                    unprocessedType.fields += processVariable(
                        typeResolver,
                        it.type,
                        it.name,
                        it.defaultValue,
                        unprocessedType,
                        it.modifiers ?: listOf()
                    )
                }

                is ASTNode.MultiVariableDeclaration -> {
                    unprocessedType.fields += it.variables.map { v ->
                        processVariable(
                            typeResolver,
                            v.type,
                            v.name,
                            v.defaultValue,
                            unprocessedType,
                            it.modifiers ?: listOf()
                        )
                    }
                }

                is ASTNode.FunctionDeclaration -> {
                    unprocessedType.methods += processFunction(typeResolver, it, unprocessedType)
                }

                is ASTNode.ConstructorDeclaration -> {
                    unprocessedType.methods += processConstructor(typeResolver, it, unprocessedType)
                }

                is ASTNode.OperatorDeclaration -> {
                    unprocessedType.methods += processOperator(typeResolver, it, unprocessedType)
                }

                is ASTNode.UnpackingVariableDeclaration ->
                    TODO("to be implemented later in development")

                // skipped. processed as standalone types
                is ASTNode.ClassDeclaration -> {}
                is ASTNode.DecoratorDeclaration -> {}
                is ASTNode.ExtensionDeclaration -> {}
                is ASTNode.InterfaceDeclaration -> {}
            }
        }
    }

    @Suppress("DuplicatedCode")
    private fun processFunction(
        typeResolver: AbstractTypeResolver,
        decl: ASTNode.FunctionDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            decl.name,
            accessFlags = decl.modifiers?.map { AccessFlag.valueOf(it::class.simpleName!!.uppercase()) }?.toMutableList() ?: mutableListOf()
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeResolver::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodTypeResolver = typeResolverFactory.get()(
            typeResolver,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += processParameters(decl.parameters, methodTypeResolver)

        if (type.attributes.contains<ExtensionAttribute>()) {
            method.parameters.addFirst(Parameter.of("this", type.attributes.get<ExtensionAttribute>()!!.type))
            method.accessFlags += AccessFlag.STATIC
        }

        decl.returnType
            ?.let { typeResolver.getType(it) }
            ?.also { method.returnType = it }

        decl.codeBlock
            ?.let { CodeBlockAttribute(it, methodTypeResolver) }
            ?.also { method.attributes += it }

        if (type.isInterface) {
            method.accessFlags += AccessFlag.PUBLIC
        }
        if (decl.codeBlock == null) {
            method.accessFlags += AccessFlag.ABSTRACT
        }

        return method
    }

    private fun processParameters(
        parameters: List<ASTNode.Parameter>,
        methodTypeResolver: AbstractTypeResolver
    ): List<Parameter> = parameters.map {
        Parameter.of(
            it.name,
            methodTypeResolver.getType(it.type),
            it.defaultValue
                ?.let { expr -> DefaultValueAttribute(expr) }
                ?.let { attr -> arrayOf(attr) }
                ?: Utils.EMPTY_ATTRIBUTES
        )
    }

    private fun processConstructor(
        typeGetter: AbstractTypeResolver,
        decl: ASTNode.ConstructorDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            "<init>",
            accessFlags = decl.modifiers?.map { AccessFlag.valueOf(it::class.simpleName!!.uppercase()) }?.toMutableList() ?: mutableListOf()
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeGetter::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodAbstractTypeResolver = typeResolverFactory.get()(
            typeGetter,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += decl.parameters?.map {
            Parameter.of(
                it.name,
                methodAbstractTypeResolver.getType(it.type),
                it.defaultValue
                    ?.let { expr -> DefaultValueAttribute(expr) }
                    ?.let { attr -> arrayOf(attr) }
                    ?: Utils.EMPTY_ATTRIBUTES
            )
        } ?: emptyList()

        CodeBlockAttribute(decl.codeBlock, methodAbstractTypeResolver)
            .also { method.attributes += it }

        return method
    }

    // I can't get rid of it so yeah
    private fun processOperator(
        typeGetter: AbstractTypeResolver,
        decl: ASTNode.OperatorDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            decl.name.value,
            accessFlags = decl.modifiers?.map { AccessFlag.valueOf(it::class.simpleName!!.uppercase()) }?.toMutableList() ?: mutableListOf()
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeGetter::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodAbstractTypeResolver = typeResolverFactory.get()(
            typeGetter,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += decl.parameters.map {
            Parameter.of(
                it.name,
                methodAbstractTypeResolver.getType(it.type),
                it.defaultValue
                    ?.let { expr -> DefaultValueAttribute(expr) }
                    ?.let { attr -> arrayOf(attr) }
                    ?: Utils.EMPTY_ATTRIBUTES
            )
        }

        if (type.attributes.contains<ExtensionAttribute>()) {
            method.parameters.addFirst(Parameter.of("this", type.attributes.get<ExtensionAttribute>()!!.type))
            method.accessFlags += AccessFlag.STATIC
        }

        decl.returnType
            ?.let { typeGetter.getType(it) }
            ?.also { method.returnType = it }

        decl.codeBlock
            ?.let { CodeBlockAttribute(it, methodAbstractTypeResolver) }
            ?.also { method.attributes += it }

        method.attributes += OperatorTemplate

        return method
    }

    private fun processVariable(
        typeGetter: AbstractTypeResolver,
        variableType: ASTNode.TypeExpr,
        variableName: String,
        defaultValue: ASTNode.Expression?,
        unprocessedType: MutableType,
        modifiers: List<ASTNode.Modifier>
    ): MutableField {
        if (unprocessedType.attributes.contains<ExtensionAttribute>())
            error("Extension fields are not supported yet")

        val fieldType = typeGetter.getType(variableType)

        val field = MutableField(
            unprocessedType,
            variableName,
            fieldType,
            accessFlags = modifiers.map { AccessFlag.valueOf(it::class.simpleName!!.uppercase()) }.toMutableList()
        )

        defaultValue
            ?.let { expr -> DefaultValueAttribute(expr) }
            ?.also { attr -> field.attributes += attr }

        return field
    }

    private fun getTypeResolver(type: ProcessedType) = typeResolverFactory.get()(
        availableTypes[type.file]!! +
            type.type.typeParameters().map(TypeParameter::toTemplate).toSet() +
            type.file.imports.types.values
    )

    private fun AurumFile.importedTypes(): List<Type> = processedTypes.get().map(ProcessedType::type)
        .filter { it.fullName() in this.imports.values.map(ASTNode.QualifiedName::toString) }
}