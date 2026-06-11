package aurum.lang.compiler.frontend.stages.analyzing

import aurum.lang.compiler.frontend.stages.*
import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import java.nio.file.Path

class PackageProcessingStage : Stage() {
    val compilationData = input<CompilationData>()
    val files = input<Files>()
    val outFiles = output<Files>()
    val outPackages = output<Packages>()

    init {
        dependsOn<ImportProcessingStage>()
    }

    override fun execute() {
        outFiles.set(Files(files.get().files.map(::processFile)))

        val processedPackages = processPackages()
        outPackages.set(Packages(processedPackages.values.toList()))
    }

    private fun processPackages(): MutableMap<Path, PackageArtifact> {
        val allPackages = outFiles.get().files.groupBy { it.path.parent }

        val processedPackages = mutableMapOf<Path, PackageArtifact>()

        fun processPackageDirectory(pkg: Path, files: List<AurumFile>): PackageArtifact {
            if (pkg !in processedPackages) {
                val packageArtifact = PackageArtifact(
                    pkg,
                    files,
                    allPackages
                        .filter { (k, _) -> k.parent == pkg }
                        .map { (k, v) -> processPackageDirectory(k, v) }
                )
                processedPackages[pkg] = packageArtifact
            }

            return processedPackages[pkg]!!
        }

        allPackages.forEach { (pkg, files) -> processPackageDirectory(pkg, files) }
        return processedPackages
    }

    private fun processFile(file: AurumFile): AurumFile {
        file.pkg = file.ast.find { it is ASTNode.Package }
            ?.let { (it as ASTNode.Package).packageName.toString() }
            ?: (file.path.minusElement(compilationData.get().workDir)).dropLast(1).joinToString(".")

        return file
    }
}