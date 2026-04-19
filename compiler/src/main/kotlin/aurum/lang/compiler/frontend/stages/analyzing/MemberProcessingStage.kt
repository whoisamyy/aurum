package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.MutableField
import aurum.lang.compiler.frontend.model.MutableMethod
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.model.Parameter
import aurum.lang.model.Type
import aurum.lang.model.TypeParameter
import aurum.lang.model.Types
import aurum.lang.model.attribute.PrimaryConstructorAttribute
import aurum.lang.model.impl.Utils

class MemberProcessingStage : Stage() {
    val definedPackages = input<DefinedPackages>()
    val processedTypes = input<ProcessedTypes>()

//    val processedTypes = output<ProcessedTypes>()

    init {
        dependsOn<TypeProcessingStage>()
    }

    private val availableTypes by lazy {
        processedTypes.get().groupBy(ProcessedType::file)
            .mapValues { (file, types) -> types.map(ProcessedType::type).toSet() + file.importedTypes() }
    }

    override fun execute() {
        val processed = processedTypes.get()
            .map {
                val type = processTypeMembers(it)
                ProcessedType(type, it.declaration, it.file)
            }
            .toSet()

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

        val typeGetter = getTypeGetter(type)

        val members = declaration.members

        processImplicitConstructor(declaration, unprocessedType, typeGetter)

        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    private fun processImplicitConstructor(
        declaration: ASTNode.ClassDeclaration,
        unprocessedType: MutableType,
        typeGetter: TypeGetter
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
    }

    fun processInterfaceMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.InterfaceDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeGetter = getTypeGetter(type)

        val members = declaration.members
        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    fun processExtensionMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.ExtensionDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeGetter = getTypeGetter(type)

        val members = declaration.members
        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    fun processDecoratorMembers(type: ProcessedType): Type {
        val declaration = type.declaration as ASTNode.DecoratorDeclaration
        val unprocessedType = type.type as? MutableType ?: return type.type // only mutable types need to be processed

        val typeGetter = getTypeGetter(type)

        // todo:
//        processParameters(declaration.parameters ?: listOf(), typeGetter)
//            .map {  }

//        val members = declaration.members
//        processMembers(members, unprocessedType, typeGetter)

        return unprocessedType
    }

    private fun processMembers(
        members: List<ASTNode.MemberDeclaration>?,
        unprocessedType: MutableType,
        typeGetter: TypeGetter
    ) {
        members?.forEach {
            when (it) {
                is ASTNode.SingleTypedVariable -> {
                    unprocessedType.fields += processVariable(
                        typeGetter,
                        it.type,
                        it.name,
                        it.defaultValue,
                        unprocessedType
                    )
                }

                is ASTNode.MultiVariableDeclaration -> {
                    unprocessedType.fields += it.variables.map { v ->
                        processVariable(
                            typeGetter,
                            v.type,
                            v.name,
                            v.defaultValue,
                            unprocessedType
                        )
                    }
                }

                is ASTNode.FunctionDeclaration -> {
                    unprocessedType.methods += processFunction(typeGetter, it, unprocessedType)
                }

                is ASTNode.ConstructorDeclaration -> {
                    unprocessedType.methods += processConstructor(typeGetter, it, unprocessedType)
                }

                is ASTNode.OperatorDeclaration -> {
                    unprocessedType.methods += processOperator(typeGetter, it, unprocessedType)
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
        typeGetter: TypeGetter,
        decl: ASTNode.FunctionDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            decl.name
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeGetter::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodTypeGetter = TypeGetter(
            typeGetter,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += processParameters(decl.parameters, methodTypeGetter)

        decl.returnType
            ?.let { typeGetter.getType(it) }
            ?.also { method.returnType = it }

        decl.codeBlock
            ?.let { CodeBlockAttribute(it) }
            ?.also { method.attributes += it }

        return method
    }

    private fun processParameters(
        parameters: List<ASTNode.Parameter>,
        methodTypeGetter: TypeGetter
    ): List<Parameter> = parameters.map {
        Parameter.of(
            it.name,
            methodTypeGetter.getType(it.type),
            it.defaultValue
                ?.let { expr -> DefaultValueAttribute(expr) }
                ?.let { attr -> arrayOf(attr) }
                ?: Utils.EMPTY_ATTRIBUTES
        )
    }

    private fun processConstructor(
        typeGetter: TypeGetter,
        decl: ASTNode.ConstructorDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            "<init>"
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeGetter::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodTypeGetter = TypeGetter(
            typeGetter,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += decl.parameters?.map {
            Parameter.of(
                it.name,
                methodTypeGetter.getType(it.type),
                it.defaultValue
                    ?.let { expr -> DefaultValueAttribute(expr) }
                    ?.let { attr -> arrayOf(attr) }
                    ?: Utils.EMPTY_ATTRIBUTES
            )
        } ?: emptyList()

        CodeBlockAttribute(decl.codeBlock)
            .also { method.attributes += it }

        return method
    }

    // i can't get rid of it so yeah
    @Suppress("DuplicatedCode")
    private fun processOperator(
        typeGetter: TypeGetter,
        decl: ASTNode.OperatorDeclaration,
        type: MutableType
    ): MutableMethod {
        val method = MutableMethod(
            type,
            decl.name.value
        )

        method.typeParameters = decl.typeParams
            ?.map {
                TypeParameter.of(it.name, it.bound?.let(typeGetter::getType) ?: Types.OBJECT)
            }
            ?.toMutableList()
            ?: mutableListOf()

        val methodTypeGetter = TypeGetter(
            typeGetter,
            method.typeParameters.map(TypeParameter::toTemplate).toSet()
        )

        method.parameters += decl.parameters.map {
            Parameter.of(
                it.name,
                methodTypeGetter.getType(it.type),
                it.defaultValue
                    ?.let { expr -> DefaultValueAttribute(expr) }
                    ?.let { attr -> arrayOf(attr) }
                    ?: Utils.EMPTY_ATTRIBUTES
            )
        }

        decl.returnType
            ?.let { typeGetter.getType(it) }
            ?.also { method.returnType = it }

        decl.codeBlock
            ?.let { CodeBlockAttribute(it) }
            ?.also { method.attributes += it }

        method.attributes += OperatorTemplate

        return method
    }

    private fun processVariable(
        typeGetter: TypeGetter,
        variableType: ASTNode.TypeExpr,
        variableName: String,
        defaultValue: ASTNode.Expression?,
        unprocessedType: MutableType
    ): MutableField {
        val fieldType = typeGetter.getType(variableType)

        val field = MutableField(
            unprocessedType,
            variableName,
            fieldType
        )

        defaultValue
            ?.let { expr -> DefaultValueAttribute(expr) }
            ?.also { attr -> field.attributes += attr }

        return field
    }

    private fun getTypeGetter(type: ProcessedType) = TypeGetter(
        availableTypes[type.file]!! +
            type.type.typeParameters().map(TypeParameter::toTemplate).toSet()
    )

    private fun AurumFile.importedTypes(): List<Type> = processedTypes.get().map(ProcessedType::type)
        .filter { it.fullName() in this.imports.values.map(ASTNode.QualifiedName::toString) }
}