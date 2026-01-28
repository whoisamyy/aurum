package lang.aurum.parsing.stages

import lang.aurum.ir.ConstantPool
import lang.aurum.model.Method
import lang.aurum.model.Type
import lang.aurum.model.Types
import lang.aurum.model.impl.PrimitiveTypeImpl
import lang.aurum.parsing.antlr.AurumLexer
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.model.MutableTypePool
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.lang.reflect.AccessFlag
import java.nio.file.Path
import java.util.stream.IntStream
import kotlin.io.path.nameWithoutExtension

abstract class AbstractParsingContext

data class ParsingContext (
    val workDir: Path,
    val classPath: Set<Path>,
    val files: LinkedHashSet<FileContext>
) : AbstractParsingContext() {
    override fun toString(): String {
        return "ParsingContext(workDir=$workDir, classPath=$classPath, files=$files)"
    }
}

data class FileContext (
    val parsingContext: ParsingContext,
    val path: Path,
    val pkg: String?,
    val ctx: AurumParser.ProgramContext,
) : AbstractParsingContext() {
    val importMap: ImportMap = ImportMap(
        mutableMapOf(
            "void" to PrimitiveTypeImpl.VOID,
            "bool" to PrimitiveTypeImpl.BOOLEAN,
            "byte" to PrimitiveTypeImpl.BYTE,
            "short" to PrimitiveTypeImpl.SHORT,
            "char" to PrimitiveTypeImpl.CHAR,
            "int" to PrimitiveTypeImpl.INT,
            "float" to PrimitiveTypeImpl.FLOAT,
            "long" to PrimitiveTypeImpl.LONG,
            "double" to PrimitiveTypeImpl.DOUBLE,
            "object" to Types.OBJECT,
            "string" to Types.STRING,
            "System" to Type.ofClass(System::class.java),
        ),
        mutableMapOf(
            "range" to mutableSetOf(
                Method.of(IntStream::class.java.getMethod("range", Int::class.java, Int::class.java)),
            )
        ),
        symbolMap = mutableMapOf(
            "println" to "java.lang.System.out.println",
            "printf" to "java.lang.System.out.printf",
            "print" to "java.lang.System.out.print",
        )
    )
    val classes: MutableMap<Type, TypeDeclCtx<*>> = mutableMapOf()
    val typeDefs: MutableMap<String, Type> = mutableMapOf()
    val externalLinks: MutableSet<Path> = mutableSetOf()

    val fileClass: MutableType = MutableTypePool.get(
        path.nameWithoutExtension,
        pkg,
        accessFlags = mutableListOf(AccessFlag.PUBLIC, AccessFlag.FINAL)
    )

    init {
        classes += fileClass to FileClassDeclCtx(ctx.declaration())
    }

    val constantPool: ConstantPool = ConstantPool()

    companion object {
        /**
         * Creates new [FileContext]. Only [path] and [ctx] fields are filled, other fields left empty and not null
         */
        fun ofPath(parsingContext: ParsingContext, path: Path): FileContext {
            val program = getParser(path)
            return FileContext(
                parsingContext,
                path,
                program.packageDecl()?.qualifiedName()?.text,
                program,
            )
        }
    }



    override fun toString(): String = "$path"
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileContext

        return path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}

private val parserCache: MutableMap<Path, AurumParser.ProgramContext> = mutableMapOf()

fun getParser(path: Path): AurumParser.ProgramContext {
    if (parserCache.containsKey(path))
        return parserCache[path]!!
    val input = CharStreams.fromPath(path)
    val lexer = AurumLexer(input)
    val tokens = CommonTokenStream(lexer)
    val parser = AurumParser(tokens)

    parserCache[path] = parser.program()
    return parserCache[path]!!
}

sealed class TypeDeclCtx<T>(open val ctx: T)
data class ClassDeclCtx(override val ctx: AurumParser.ClassDeclContext) : TypeDeclCtx<AurumParser.ClassDeclContext>(ctx)
data class InterfaceDeclCtx(override val ctx: AurumParser.InterfaceDeclContext) : TypeDeclCtx<AurumParser.InterfaceDeclContext>(ctx)
data class DecoratorDeclCtx(override val ctx: AurumParser.DecoratorDeclContext) : TypeDeclCtx<AurumParser.DecoratorDeclContext>(ctx)
data class ExtensionDeclCtx(override val ctx: AurumParser.ExtensionDeclContext) : TypeDeclCtx<AurumParser.ExtensionDeclContext>(ctx)
data class FileClassDeclCtx(override val ctx: List<AurumParser.DeclarationContext>) : TypeDeclCtx<List<AurumParser.DeclarationContext>>(ctx)
