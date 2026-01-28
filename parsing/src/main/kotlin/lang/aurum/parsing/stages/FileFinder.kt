package lang.aurum.parsing.stages

import lang.aurum.parsing.antlr.AurumParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

class FileFinder(
    val fileContext: FileContext,
    val workDir: Path,
    val classPath: Set<Path>
) {
    private companion object Cache {
        val filesCache: MutableMap<Pair<Path, List<String>>, List<Path>> = mutableMapOf()
    }

    fun findFiles(pkgName: List<String>): List<Path> {
        val foundFiles = findFiles(workDir, pkgName)
        if (foundFiles.isNotEmpty())
            return foundFiles
        val retList = mutableListOf<Path>()
        for (cp in classPath) {
            retList += findFiles(cp, pkgName)
        }

        return retList
    }

    /**
     * Main entry point. Handles caching for file lookups.
     */
    private fun findFiles(dir: Path, pkgName: List<String>): List<Path> {
        val cacheKey = dir to pkgName
        if (filesCache.containsKey(cacheKey)) {
            return filesCache[cacheKey]!!
        }

        val foundFiles = findFilesLogic(dir, pkgName)

        filesCache[cacheKey] = foundFiles
        return foundFiles
    }

    /**
     * The core logic for finding files, separated from caching.
     */
    private fun findFilesLogic(dir: Path, pkgName: List<String>): List<Path> {
        val (pathCandidate, existingPkg) = resolveHighestExistingPath(dir, pkgName)

        if (pathCandidate.notExists()) {
            fileContext.externalLinks.add(pathCandidate)
            return listOf()
        }

        val memberName = pkgName.drop(existingPkg.size)

        return if (pathCandidate.isDirectory()) {
            searchDirectoryForMembers(pathCandidate, memberName)
        } else {
            if (pathCandidate.hasMember(memberName)) {
                listOf(pathCandidate)
            } else {
                val parentPkgSpec = pathCandidate.toList().map { it.toString() }.dropLast(1)
                findFiles(dir, parentPkgSpec)
            }
        }
    }

    /**
     * Helper data class to return multiple values from resolveHighestExistingPath.
     */
    private data class ResolvedPackage(val path: Path, val foundPackage: List<String>)

    /**
     * Walks up the package structure to find the deepest
     * existing srcPath and the corresponding package parts.
     *
     * E.g., if dir="/src" and pkgName=["com", "example", "util"], but
     * only "/src/com/example" exists, this returns:
     * (srcPath="/src/com/example", foundPackage=["com", "example"])
     */
    private fun resolveHighestExistingPath(dir: Path, pkgName: List<String>): ResolvedPackage {
        var pkg = pkgName
        if (pkg.isEmpty()) {
            return ResolvedPackage(dir, pkg)
        }

        var pathCandidate = dir.resolve(Path(pkg.joinToString(separator = "/")))

        while (pathCandidate.notExists() && pkg.isNotEmpty()) {
            pkg = pkg.dropLast(1)
            pathCandidate = pathCandidate.parent
        }

        return ResolvedPackage(pathCandidate, pkg)
    }

    /**
     * Searches a specific directory for *.au files that contain the given member.
     */
    private fun searchDirectoryForMembers(dirPath: Path, memberName: List<String>): List<Path> {
        return Files.newDirectoryStream(dirPath, "*.au").use { fileStream ->
            fileStream.filter { !it.isDirectory() }
                .filter { it.hasMember(memberName) }
                .toList()
        }
    }

    private fun Path.hasMember(memberName: List<String>): Boolean {
        val program = getParser(this)
        program.children.filter { it is AurumParser.DeclarationContext }
            .forEach {
                when (it) {
                    is AurumParser.FuncDeclContext -> {
                        return it.funcSign().Identifier().text == memberName[0]
                    }
                    is AurumParser.ClassDeclContext -> {
                        return it.Identifier().text == memberName[0]
                    }
                    is AurumParser.InterfaceDeclContext -> {
                        return it.Identifier().text == memberName[0]
                    }
                    is AurumParser.DecoratorDeclContext -> {
                        return it.Identifier().text == memberName[0]
                    }
                    is AurumParser.ExtensionDeclContext -> {
                        return it.extensionMember().any { member ->
                            member.operatorDecl()?.let { op -> op.OperatorSymbol().text == memberName[0] } == true
                            || member.funcDecl()?.let { fn -> fn.funcSign().Identifier().text == memberName[0] } == true
                            || member.varDecl()?.let { field ->
                                when (field) {
                                    is AurumParser.SingleDeclContext -> {
                                        field.Identifier().text == memberName[0]
                                    }

                                    is AurumParser.UnpackDeclContext -> {
                                        field.varId().any { id -> id.Identifier().text == memberName[0] }
                                    }

                                    is AurumParser.MultiDeclContext -> {
                                        field.varIdAssignment()
                                            .map { id -> id.varId() }
                                            .any { id -> id.Identifier().text == memberName[0] }
                                    }

                                    else -> false
                                }
                            } == true
                        }
                    }
                    is AurumParser.VarDeclContext -> {
                        when (it) {
                            is AurumParser.SingleDeclContext -> {
                                return it.Identifier().text == memberName[0]
                            }
                            is AurumParser.UnpackDeclContext -> {
                                return it.varId().any { id -> id.Identifier().text == memberName[0] }
                            }
                            is AurumParser.MultiDeclContext -> {
                                return it.varIdAssignment()
                                    .map { id -> id.varId() }
                                    .any { id -> id.Identifier().text == memberName[0] }
                            }
                        }
                    }
                    is AurumParser.OperatorDeclContext -> {
                        return it.OperatorSymbol().text == memberName[0]
                    }
                    else -> return false
                }
            }
        return false
    }
}