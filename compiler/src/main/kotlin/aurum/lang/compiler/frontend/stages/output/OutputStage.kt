package aurum.lang.compiler.frontend.stages.output

import aurum.lang.compiler.frontend.stages.CompilationData
import aurum.lang.compiler.frontend.stages.Stage
import aurum.lang.compiler.frontend.stages.TargetArtifact
import aurum.lang.compiler.frontend.stages.TranslationResults
import aurum.lang.compiler.frontend.stages.translating.TranslationStage
import java.lang.reflect.Modifier
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.notExists
import kotlin.io.path.writeBytes

class OutputStage : Stage() {
    val compilationData = input<CompilationData>()
    val translationResults = input<TranslationResults>()
    val targetArtifact = input<TargetArtifact>()

    init {
        dependsOn<TranslationStage>()
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute() {
        val outDir = compilationData.get().outputDir
        translationResults.get().forEach {
            val type = it.type.type
            val file = it.type.file

            val outFile = outDir
                .resolve(file.pkg.replace('.', '/'))
                .resolve("${type.className()}${targetArtifact.get().extension}")

            if (outFile.notExists()) {
                outFile.createParentDirectories()
                outFile.createFile()
            }
            when (val res = it.result) {
                is String -> outFile.writeBytes(res.toByteArray())
                is ByteArray -> outFile.writeBytes(res)
                is Array<*> -> outFile.writeBytes((res as Array<Byte>).toByteArray())
                is Class<*> -> {
                    res.declaredMethods
                        .find { m -> m.name == "main" }
                        ?.let { method ->
                            if (Modifier.isStatic(method.modifiers)) {
                                val res = when {
                                    method.parameterCount == 0 -> {
                                        if (!method.trySetAccessible())
                                            println("Cannot access main method")
                                        else
                                            method.invoke(null)
                                    }
                                    method.parameters[0].type == Array<String>::class.javaObjectType -> method.invoke(null, null)
                                    else -> println("Nothing to invoke. Add method with this signature `main(string[])`")
                                }

                                if (method.returnType != Void::class.javaPrimitiveType)
                                    println(res) // maybe it is not needed?
                            } else {
                                val constructor = res.constructors.find { c -> c.parameterCount == 0 }
                                if (constructor == null) {
                                    println("Cannot create an instance of a class $res to execute main method")
                                    return@let
                                }
                                if (!constructor.trySetAccessible()) {
                                    println("Could not access constructor of class $res")
                                    return@let
                                }
                                val obj = constructor.newInstance()

                                val res = when {
                                    method.parameterCount == 0 -> {
                                        if (!method.trySetAccessible())
                                            println("Cannot access main method")
                                        else
                                            method.invoke(obj)
                                    }
                                    method.parameters[0].type == Array<String>::class.javaObjectType -> method.invoke(obj, null)
                                    else -> println("Nothing to invoke. Add method with this signature `main(string[])`")
                                }

                                if (method.returnType != Void::class.javaPrimitiveType)
                                    println(res) // maybe it is not needed?
                            }
                        }
                }
                else -> println(res) // I don't know what to do
            }
        }
    }
}