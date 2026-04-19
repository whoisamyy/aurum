package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.model.MutablePackage
import aurum.lang.compiler.frontend.model.MutableType
import aurum.lang.compiler.frontend.model.MutableTypePool
import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import java.lang.reflect.AccessFlag

class TypeDefiningStage : Stage() {
    val packages = input<Packages>()

    val definedTypes = output<DefinedTypes>()
    val definedPackages = output<DefinedPackages>()

    override fun execute() {
        val (packages, types) = packages.get()
            .map(::processPackage)
            .unzip()

        definedTypes.set(DefinedTypes(types.flatten().toSet()))
        definedPackages.set(DefinedPackages(packages))
    }

    private fun processPackage(pkg: PackageArtifact): Pair<MutablePackage, Set<TypeDefinition>> {
        val mutablePkg = MutablePackage(pkg.path.joinToString("."))

        val definedTypes = pkg.files.flatMap(::processFile)
        definedTypes
            .map(TypeDefinition::type)
            .forEach {
                if (it.isPrivate || it.isProtected || !it.isPublic)
                    mutablePkg.privateTypes += it
                else
                    mutablePkg.publicTypes += it
            }

        return mutablePkg to definedTypes.toSet()
    }

    private fun processFile(file: AurumFile): Set<TypeDefinition> {
        val topLevelDeclarations = file.ast.filterIsInstance<ASTNode.TypeDeclaration>()
        val types = mutableMapOf<MutableType, ASTNode.TypeDeclaration>()

        // todo: add file types and etc
//        val fileType = MutableTypePool.get(
//            file.path.nameWithoutExtension,
//            file.pkg
//        )

        processDeclarations(topLevelDeclarations, file, types)

        return types.map { (type, declaration) -> TypeDefinition(type, declaration, file) }.toSet()
    }

    private fun processDeclarations(
        topLevelDeclarations: List<ASTNode.TypeDeclaration>,
        file: AurumFile,
        types: MutableMap<MutableType, ASTNode.TypeDeclaration>
    ) {
        topLevelDeclarations.filterIsInstance<ASTNode.ClassDeclaration>()
            .forEach {
                val type = MutableTypePool.get(
                    it.name,
                    file.pkg,
                    accessFlags = it.modifiers.toAccessFlags().toMutableList()
                )
                types[type] = it

                processDeclarations(
                    it.members
                        ?.filterIsInstance<ASTNode.TypeDeclaration>()
                        ?: emptyList(),
                    file,
                    types
                )
            }

        topLevelDeclarations.filterIsInstance<ASTNode.InterfaceDeclaration>()
            .forEach {
                val accessFlags = mutableListOf(AccessFlag.INTERFACE, AccessFlag.ABSTRACT)
                accessFlags += it.modifiers.toAccessFlags().toMutableList()
                val type = MutableTypePool.get(
                    it.name,
                    file.pkg,
                    accessFlags = accessFlags
                )
                types[type] = it

                processDeclarations(
                    it.members
                        ?.filterIsInstance<ASTNode.TypeDeclaration>()
                        ?: emptyList(),
                    file,
                    types
                )
            }

        topLevelDeclarations.filterIsInstance<ASTNode.DecoratorDeclaration>()
            .forEach {
                val accessFlags = mutableListOf(AccessFlag.INTERFACE, AccessFlag.ABSTRACT, AccessFlag.ANNOTATION)
                accessFlags += it.modifiers.toAccessFlags().toMutableList()
                val type = MutableTypePool.get(
                    it.name,
                    file.pkg,
                    accessFlags = accessFlags
                )
                types[type] = it
            }

        topLevelDeclarations.filterIsInstance<ASTNode.ExtensionDeclaration>()
            .forEach {
                val type = MutableTypePool.get(
                    "extension$${it.type}",
                    file.pkg,
                    accessFlags = it.modifiers.toAccessFlags().toMutableList()
                )
                types[type] = it

                processDeclarations(
                    it.members
                        ?.filterIsInstance<ASTNode.TypeDeclaration>()
                        ?: emptyList(),
                    file,
                    types
                )
            }
    }
}

private fun List<ASTNode.Modifier>?.toAccessFlags(): Array<AccessFlag> {
    return this?.map {
        when (it) {
            is ASTNode.Modifier.Final -> AccessFlag.FINAL
            is ASTNode.Modifier.Abstract -> AccessFlag.ABSTRACT
            is ASTNode.Modifier.Static -> AccessFlag.STATIC
            is ASTNode.Modifier.Private -> AccessFlag.PRIVATE
            is ASTNode.Modifier.Protected -> AccessFlag.PROTECTED
            is ASTNode.Modifier.Public -> AccessFlag.PUBLIC
        }
    }?.toTypedArray() ?: arrayOf()
}