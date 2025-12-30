package lang.aurum.parsing.stages

import lang.aurum.model.PrimitiveType
import lang.aurum.model.Type
import lang.aurum.parsing.antlr.AurumLexer
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.ConstantPool
import lang.aurum.parsing.model.MutableType
import lang.aurum.parsing.model.MutableTypePool
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import java.lang.reflect.AccessFlag
import java.nio.file.Path
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
    val pkg: String,
    val ctx: AurumParser.ProgramContext,
) : AbstractParsingContext() {
    val importMap: ImportMap = ImportMap(
        mutableMapOf(
            "void" to PrimitiveType.VOID,
            "bool" to PrimitiveType.BOOLEAN,
            "byte" to PrimitiveType.BYTE,
            "short" to PrimitiveType.SHORT,
            "char" to PrimitiveType.CHAR,
            "int" to PrimitiveType.INT,
            "float" to PrimitiveType.FLOAT,
            "long" to PrimitiveType.LONG,
            "double" to PrimitiveType.DOUBLE,
            "string" to Type.ofClass(String::class.java),
            "object" to Type.ofClass(Object::class.java)
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
                program.packageDecl().qualifiedName().text,
                program,
            )
        }
    }

    override fun toString(): String = "$path"
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

sealed class MemberDeclCtx<T : ParserRuleContext>(open val ctx: T)
data class OperatorDeclCtx(override val ctx: AurumParser.OperatorDeclContext) : MemberDeclCtx<AurumParser.OperatorDeclContext>(ctx)
data class FuncDeclCtx(override val ctx: AurumParser.FuncDeclContext) : MemberDeclCtx<AurumParser.FuncDeclContext>(ctx)
data class VarDeclCtx(override val ctx: AurumParser.VarDeclContext) : MemberDeclCtx<AurumParser.VarDeclContext>(ctx)

sealed class TypeDeclCtx<T>(open val ctx: T)
data class ClassDeclCtx(override val ctx: AurumParser.ClassDeclContext) : TypeDeclCtx<AurumParser.ClassDeclContext>(ctx)
data class InterfaceDeclCtx(override val ctx: AurumParser.InterfaceDeclContext) : TypeDeclCtx<AurumParser.InterfaceDeclContext>(ctx)
data class DecoratorDeclCtx(override val ctx: AurumParser.DecoratorDeclContext) : TypeDeclCtx<AurumParser.DecoratorDeclContext>(ctx)
data class ExtensionDeclCtx(override val ctx: AurumParser.ExtensionDeclContext) : TypeDeclCtx<AurumParser.ExtensionDeclContext>(ctx)
data class FileClassDeclCtx(override val ctx: List<AurumParser.DeclarationContext>) : TypeDeclCtx<List<AurumParser.DeclarationContext>>(ctx)
data class TypeDefDeclCtx(override val ctx: AurumParser.TypeDefContext) : TypeDeclCtx<AurumParser.TypeDefContext>(ctx)
