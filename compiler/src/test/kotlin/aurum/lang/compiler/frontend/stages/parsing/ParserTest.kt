package aurum.lang.compiler.frontend.stages.parsing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParserTest {

    private fun parse(input: String, call: (Parser) -> Any): Any {
        val tokens = Tokenizer(input).parse()
        val result = call(Parser(tokens))
        return result
    }

    @Test
    fun `parsePackage basic`() {
        val input = "package foo.bar.baz"
        val result = parse(input) { it.parsePackage() }.toString()
        assertEquals("package foo.bar.baz", result)
    }

    @Test
    fun `parseImport without alias`() {
        val input = "import foo.bar.Baz"
        val result = parse(input) { it.parseImportStmt() }.toString()
        assertEquals("import foo.bar.Baz", result)
    }

    @Test
    fun `parseImport with alias`() {
        val input = "import foo.bar.Baz as Qux"
        val result = parse(input) { it.parseImportStmt() }.toString()
        assertEquals("import foo.bar.Baz as Qux", result)
    }

    @Test
    fun `parseModifiers multiple keywords`() {
        val input = "public static final"
        val tokens = Tokenizer(input).parse()
        val modifiers = Parser(tokens).parseModifiers()
        assertEquals(3, modifiers.size)
        assertEquals("public", modifiers[0].toString())
        assertEquals("static", modifiers[1].toString())
        assertEquals("final", modifiers[2].toString())
    }

    @Test
    fun `parseTypeExpr with array and vararg`() {
        val input = "String[]..."
        val result = parse(input) { it.parseTypeExpr() }.toString()
        assertEquals("String[]...", result)
    }

    @Test
    fun `parseTypeExpr intersection and union`() {
        val input = "A & B | C"
        val result = parse(input) { it.parseTypeExpr() }.toString()
        assertEquals("A & B | C", result)
    }

    @Test
    fun `parseTypeArg wildcard without bound`() {
        val input = "?"
        val result = parse(input) { it.parseTypeArg() }.toString()
        assertEquals("?", result)
    }

    @Test
    fun `parseTypeArg wildcard with extends`() {
        val input = "? extends Entity"
        val result = parse(input) { it.parseTypeArg() }.toString()
        assertEquals("? : Entity", result)
    }

    @Test
    fun `parseTypeArg wildcard with colon`() {
        val input = "? : Entity"
        val result = parse(input) { it.parseTypeArg() }.toString()
        assertEquals("? : Entity", result)
    }

    @Test
    fun `parseTypeExpr with generics and wildcards`() {
        val input = "Map[String, ? extends Entity]"
        val result = parse(input) { it.parseTypeExpr() }.toString()
        assertEquals("Map[String, ? : Entity]", result)
    }

    @Test
    fun `parseTypeExpr nested lambdas`() {
        val input = "(Int) -> (String) -> Unit"
        val result = parse(input) { it.parseTypeExpr() }.toString()
        assertEquals("(Int) -> (String) -> Unit", result)
    }

    @Test
    fun `parseTypeDef basic`() {
        val input = "type Callback = (Int, String) -> Void"
        val result = parse(input) { it.parseTypeDef() }.toString()
        assertEquals("type Callback = (Int, String) -> Void", result)
    }

    @Test
    fun `parseExpression parses numeric literals`() {
        assertEquals("42", parse("42") { it.parseExpression() }.toString())
        assertEquals("3.14", parse("3.14") { it.parseExpression() }.toString())
    }

    @Test
    fun `parseExpression collections and named tuples`() {
        assertEquals("[]", parse("[]") { it.parseExpression() }.toString())
        assertEquals("[:]", parse("[:]") { it.parseExpression() }.toString())
        assertEquals("[1, 2, 3]", parse("[1, 2, 3]") { it.parseExpression() }.toString())
        assertEquals("(x : 1, y : 2)", parse("(x: 1, y: 2)") { it.parseExpression() }.toString())
    }

    @Test
    fun `parseMember function declaration`() {
        val input = "fn calculate(x: Int, y: Int): Float"
        val result = parse(input) { it.parseMember()!! }.toString()
        assertEquals("fn calculate(x: Int, y: Int): Float", result)
    }

    @Test
    fun `parseMember variable declaration`() {
        val input = "let x: Int = 10"
        val result = parse(input) { it.parseMember()!! }.toString()
        assertEquals("let x: Int = 10", result)
    }

    @Test
    fun `parseMember decorator declaration`() {
        val input = "@class MyDecorator(name: String)"
        val result = parse(input) { it.parseMember()!! }.toString()
        assertEquals("@class MyDecorator(name: String)", result)
    }

    @Test
    fun `parseMember multiple variable declaration`() {
        val input = "var a: Int, b: String"
        val result = parse(input) { it.parseMember()!! }.toString()
        assert(result.contains("a: Int"))
        assert(result.contains("b: String"))
    }

    @Test
    fun `parseMember constructor`() {
        val input = "init(name: String) {}"
        val result = parse(input) { it.parseMember()!! }.toString()
        assertEquals("init(name: String) {}", result)
    }

    @Test
    fun `parseMember operator overload`() {
        val input = "operator + (other: Complex): Complex"
        val result = parse(input) { it.parseMember()!! }.toString()
        assertEquals("operator +(other: Complex): Complex", result)
    }

    @Test
    fun `parseMember with decorators and modifiers`() {
        val input = """
            @Deprecated
            @Author(name)
            private static fn oldMethod()
        """.trimIndent()
        val result = parse(input) { it.parseMember()!! }.toString()
        assert(result.contains("@Deprecated"))
        assert(result.contains("private static fn oldMethod"))
    }

    @Test
    fun `parseExtension basic`() {
        val input = ": String { fn hello() }"

        val result = parse(input) { it.parseMember()!! }.toString()
        assert(result.startsWith(": String"))
    }

    @Test
    fun `parseExpression recursive postfix`() {
        val input = """
            system.out.println()
        """.trimIndent()

        val result = parse(input) { it.parseExpression() }
        assert(result is ASTNode.FunctionCall)
    }

    @Test
    fun `parseExpression newline paren as another expression after binary`() {
        val input = """
            a + b
            (c + d)
        """.trimIndent()
        val result = parse(input) { it.parseExpression() }
        assertEquals("a + b", result.toString())
    }

    @Test
    fun `parseExpression newline call on identifier`() {
        val input = """
            foo
            (1, 2)
        """.trimIndent()
        val result = parse(input) { it.parseExpression() }
        assertEquals("foo(1, 2)", result.toString())
    }

    @Test
    fun `parseExpression newline between statements`() {
        val input = """
            a + b
            c + d
        """.trimIndent()
        val result = parse(input) { it.parseExpression() }
        assertEquals("a + b", result.toString())
    }

    @Test
    fun `parseExpression stop at semicolon`() {
        val result = parse("a + b; c + d") { it.parseExpression() }
        assertEquals("a + b", result.toString())
    }

    @Test
    fun `parseCodeBlock statements separated by newline`() {
        val input = """
            {
                let x = 1
                let y = 2
            }
        """.trimIndent()
        val block = parse(input) { it.parseCodeBlock()!! } as ASTNode.PlainBlock
        assertEquals(2, block.statements!!.size)
    }
}