package lang.aurum.parsing.stages

import lang.aurum.model.Field
import lang.aurum.model.Member
import lang.aurum.model.Method
import lang.aurum.model.PrimitiveType
import lang.aurum.model.Type
import lang.aurum.parsing.Argument
import lang.aurum.parsing.antlr.AurumLexer
import lang.aurum.parsing.antlr.AurumParser
import lang.aurum.parsing.model.ConstantPool
import lang.aurum.parsing.model.MutableType
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext
import java.nio.file.Path

abstract class AbstractParsingContext

data class ParsingContext (
    val workDir: Path,
    val classPath: Set<Path>,
    val arguments: Set<Argument>,
    val files: LinkedHashSet<FileContext>
) : AbstractParsingContext()

data class FileContext (
    val parsingContext: ParsingContext,
    val path: Path,
    val pkg: String,
    val ctx: AurumParser.ProgramContext,
) : AbstractParsingContext() {
    val typeImportMap: MutableMap<String, Type> = mutableMapOf(
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
    val methodImportMap: MutableMap<String, Method> = mutableMapOf()
    val fieldImportMap: MutableMap<String, Field> = mutableMapOf()
    val classes: MutableMap<Type, TypeDeclCtx<*>> = mutableMapOf()
    val members: MutableList<Pair<Member, MemberDeclCtx<*>>> = mutableListOf()
    val typeDefs: MutableList<Pair<String, Type>> = mutableListOf()
    val externalLinks: MutableSet<Path> = mutableSetOf()

    lateinit var fileClass: MutableType
    lateinit var constantPool: ConstantPool

    fun initializeConstantPool() {
        if (!::constantPool.isInitialized)
            this.constantPool = ConstantPool()
    }

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

    override fun toString(): String {
        return "FileContext(typeDefs=$typeDefs, members=$members, classes=$classes, fieldImportMap=$fieldImportMap, methodImportMap=$methodImportMap, typeImportMap=$typeImportMap, ctx=$ctx, pkg='$pkg', path=$path, fileClass=$fileClass)"
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
