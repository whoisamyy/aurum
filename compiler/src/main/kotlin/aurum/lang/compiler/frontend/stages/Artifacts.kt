@file:Suppress("JavaDefaultMethodsNotOverriddenByDelegation")

package aurum.lang.compiler.frontend.stages

import aurum.lang.compiler.frontend.stages.analyzing.ImportMap
import aurum.lang.compiler.frontend.stages.parsing.AST
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.compiler.frontend.stages.parsing.Token
import aurum.lang.model.Package
import aurum.lang.model.Type
import java.nio.file.Path
import kotlin.io.path.isDirectory

data class ArtifactList<T> (
    val list: List<T>
) : Artifact, List<T> by list

data class Source (
    val path: Path
) : Artifact {
    val isDirectory = path.isDirectory()
}

data class AurumFile (
    val path: Path,
    val contents: String,
) : Artifact {
    lateinit var pkg: String
    lateinit var tokens: List<Token>
    lateinit var ast: AST
    lateinit var imports: ImportMap
}

data class PackageArtifact (
    val path: Path,
    val files: List<AurumFile>,
    val innerPackages: List<PackageArtifact>
) : Artifact

data class Packages (
    val packages: List<PackageArtifact>
) : Artifact, List<PackageArtifact> by packages

data class DefinedTypes (
    val types: Set<TypeDefinition>
) : Artifact, Set<TypeDefinition> by types

data class ProcessedTypes (
    val types: Set<ProcessedType>
) : Artifact, Set<ProcessedType> by types

data class TypeDefinition (
    val type: Type,
    val declaration: ASTNode.TypeDeclaration,
    val file: AurumFile
)

data class ProcessedType (
    val type: Type,
    val declaration: ASTNode.TypeDeclaration,
    val file: AurumFile
)

data class DefinedPackages (
    val packages: List<Package>
) : Artifact, List<Package> by packages

data class Files (
    val files: List<AurumFile>
) : Artifact, List<AurumFile> by files

data class TokenList (
    val tokens: List<Token>
) : Artifact, List<Token> by tokens

data class CompilationData (
    val workDir: Path,
    val outputDir: Path,
    val optimisationLevel: Int
) : Artifact