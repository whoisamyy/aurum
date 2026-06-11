package aurum.lang.compiler.backend.ir

import aurum.lang.compiler.frontend.stages.parsing.ASTNode
import aurum.lang.ir.*
import aurum.lang.model.Type
import aurum.lang.model.Types
import aurum.lang.model.UnionType

/**
 * Lowers `if` / `match` to branches, labels, and optional result slots.
 */
internal class ControlFlowCompiler(
    private val compiler: IRCompiler,
) {
    private val ir get() = compiler.irBuilder
    private val instructions get() = compiler.instructionList

    fun compileIfStatement(stmt: ASTNode.If) {
        compileIfBranches(stmt, asExpression = false, null)
    }

    fun compileIfExpression(expr: ASTNode.If): Value {
        expr.elseBlock ?: error("If expression requires an else branch")
        val resultType = unionTypes(collectIfBranchTypes(expr))
        if (resultType == Types.VOID)
            error("If expression branches must produce a value")
        val result = compiler.createVariable("if#${expr.hashCode() xor instructions.size}", resultType)
        compileIfBranches(expr, asExpression = true, result)
        return result
    }

    fun compileMatchStatement(stmt: ASTNode.Match) {
        compileMatch(stmt, asExpression = false, null)
    }

    fun compileMatchExpression(expr: ASTNode.Match): Value {
        val resultType = unionTypes(expr.cases.map { compiler.astCodeBlockType(it.block) })
        if (resultType == Types.VOID)
            error("Match expression cases must produce a value")
        val result = compiler.createVariable("match#${expr.hashCode() xor instructions.size}", resultType)
        compileMatch(expr, asExpression = true, result)
        return result
    }

    fun compileCodeBlockValue(block: ASTNode.CodeBlock): Value =
        when (block) {
            is ASTNode.Expression -> compiler.compileExpression(block)
            is ASTNode.ExpressionBlock -> {
                block.statements?.forEach(compiler::compileStatement)
                compiler.compileExpression(block.expression)
            }
            is ASTNode.PlainBlock -> {
                block.statements?.forEach(compiler::compileStatement)
                Value(NullRef, Types.VOID)
            }
            is ASTNode.Statement -> {
                compiler.compileStatement(block)
                Value(NullRef, Types.VOID)
            }
        }

    private fun collectIfBranchTypes(expr: ASTNode.If): List<Type> {
        val types = mutableListOf(compiler.astCodeBlockType(expr.block))
        expr.elseIfs?.forEach { types += compiler.astCodeBlockType(it.block) }
        expr.elseBlock?.let { types += compiler.astCodeBlockType(it) }
        return types
    }

    private fun compileIfBranches(
        stmt: ASTNode.If,
        asExpression: Boolean,
        result: Variable?,
    ) {
        val endLabel = Label("if#${stmt.hashCode() xor instructions.size}#end")

        fun finishTakenBranch(block: ASTNode.CodeBlock, scopeName: String) {
            if (asExpression) {
                val value = compileCodeBlockValue(block)
                ir += Move(result!!.reference, value.ref())
            } else {
                compiler.enterScope(Scope(scopeName, compiler.currentScope))
                compiler.compileCodeBlock(block)
                compiler.exitScope()
            }
            ir += Jump(endLabel)
        }

        val base = { stmt.hashCode() xor instructions.size }
        var nextLabel = Label("if#${base()}#cond0")
        ir += JumpIfN(compiler.compileExpression(stmt.condition).ref(), nextLabel)
        finishTakenBranch(stmt.block, "if#${base()}")
        ir += LabelInst(nextLabel)

        stmt.elseIfs?.forEachIndexed { index, elif ->
            nextLabel = Label("if#${base()}#elif$index")
            ir += JumpIfN(compiler.compileExpression(elif.condition).ref(), nextLabel)
            finishTakenBranch(elif.block, "elif#${base()}#$index")
            ir += LabelInst(nextLabel)
        }

        if (asExpression) {
            finishTakenBranch(stmt.elseBlock!!, "else#${base()}")
        } else if (stmt.elseBlock != null) {
            compiler.enterScope(Scope("else#${base()}", compiler.currentScope))
            compiler.compileCodeBlock(stmt.elseBlock)
            compiler.exitScope()
        }

        ir += LabelInst(endLabel)
    }

    private fun compileMatch(
        stmt: ASTNode.Match,
        asExpression: Boolean,
        result: Variable?,
    ) {
        val subject = compiler.materializeToVariable(
            compiler.compileExpression(stmt.what),
            "match#${stmt.hashCode() xor instructions.size}#subj",
        )
        val endLabel = Label("match#${stmt.hashCode() xor instructions.size}#end")
        var failLabel: Label? = null

        val base = { stmt.hashCode() xor instructions.size }
        stmt.cases.forEachIndexed { index, case ->
            if (failLabel != null)
                ir += LabelInst(failLabel)
            val caseFail = Label("match#${base()}#case$index#fail")
            val scoped = compileMatchCase(subject, case, caseFail)
            if (asExpression) {
                val value = compileCodeBlockValue(case.block)
                ir += Move(result!!.reference, value.ref())
            } else {
                compiler.compileCodeBlock(case.block)
            }
            if (scoped)
                compiler.exitScope()
            ir += Jump(endLabel)
            failLabel = caseFail
        }

        if (failLabel != null)
            ir += LabelInst(failLabel)
        ir += LabelInst(endLabel)
    }

    private fun compileMatchCase(
        subject: Variable,
        case: ASTNode.MatchCase,
        failLabel: Label,
    ): Boolean {
        when (val pattern = case.pattern) {
            is ASTNode.TypePattern -> {
                val type = compiler.typeResolver.getType(pattern.type)
                val instanceCheck = compiler.createVariable(
                    "match#is#${instructions.size}",
                    Types.BOOLEAN,
                )
                ir.emit(
                    InstanceOf(
                        instanceCheck.reference,
                        subject.ref(),
                        compiler.cp.getReference(type),
                    ),
                    instanceCheck,
                )
                ir += JumpIfN(instanceCheck.ref(), failLabel)
                compiler.enterScope(Scope("match#${case.hashCode() xor instructions.size}", compiler.currentScope))
                val binding = compiler.createVariable(pattern.identifier, type)
                ir.emit(
                    Cast(binding.reference, subject.ref(), compiler.cp.getReference(type)),
                    binding,
                )
                compileWhenGuards(pattern.whens, failLabel)

                return true
            }

            is ASTNode.ExpressionPattern -> {
                val patternValue = compiler.compileExpression(pattern.expression)
                val equals = compiler.compileBinaryOperation(
                    subject,
                    pattern.expression,
                    "==",
                    patternValue,
                )
                ir += JumpIfN(equals.ref(), failLabel)
                compileWhenGuards(pattern.whens, failLabel)
                return false
            }

            is ASTNode.DefaultPattern -> {
                compileWhenGuards(pattern.whens, failLabel)
                return false
            }
        }
    }

    private fun compileWhenGuards(
        whens: List<ASTNode.Expression>?,
        failLabel: Label,
    ) {
        whens?.forEach { guard ->
            ir += JumpIfN(compiler.compileExpression(guard).ref(), failLabel)
        }
    }

    private fun unionTypes(types: List<Type>): Type =
        when {
            types.isEmpty() -> Types.VOID
            types.all { it == Types.VOID } -> Types.VOID
            else -> {
                val nonVoid = types.filter { it != Types.VOID }
                when (nonVoid.size) {
                    0 -> Types.VOID
                    1 -> nonVoid[0]
                    else -> UnionType.ofTypeModels(nonVoid.toTypedArray()).superClass()
                }
            }
        }
}
