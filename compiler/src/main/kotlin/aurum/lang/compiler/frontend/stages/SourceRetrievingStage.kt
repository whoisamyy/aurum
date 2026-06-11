package aurum.lang.compiler.frontend.stages

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class SourceRetrievingStage : Stage() {
    val src = input<Source>()
    val out = output<Files>()

    override fun execute() {
        val files = mutableListOf<AurumFile>()
        val source = src.get()

        fun getContentsRecursively(path: Path) {
            if (path.isDirectory()) {
                path.listDirectoryEntries("*.au")
                    .forEach {
                        files += AurumFile(it, it.readText())
                    }

                path.listDirectoryEntries()
                    .filter { it.isDirectory() }
                    .forEach {
                        getContentsRecursively(it)
                    }
            } else
                files += AurumFile(path, path.readText())
        }

        getContentsRecursively(source.path)

        out.set(Files(files))
    }
}