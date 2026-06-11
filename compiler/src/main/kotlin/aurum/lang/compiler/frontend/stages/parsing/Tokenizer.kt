package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.TokenList

class Tokenizer (
    val input: String
) {
    fun parse(): TokenList {
        val tokens = mutableListOf<Token>()
        val lines = input.split("\n", "\r\n")
        lines.forEachIndexed { i, line ->
            tokens += parseLine(line, i + 1)
            tokens += Token.EndLine(i + 1, line.length + 1)
        }
        return TokenList(tokens.toList())
    }

    private fun parseLine(line: String, lineIndex: Int): List<Token> {
        var processed = line
        val tokens = mutableListOf<Token>()
        var char = 1
        while (processed.isNotEmpty()) {
            val ranges: MutableMap<IntRange, TokenType> = mutableMapOf()
            for (token in TokenType.entries) {
                val range = token.regex.find(processed)?.range ?: continue
                ranges[range] = token
            }

            val (range, tokenType) = ranges.toList().minByOrNull { (range, _) ->
                range.first
            } ?: error("npe")

            val mappedRange = range.map { it + char }
            val token = tokenType(lineIndex, mappedRange.first())

            if (token.value.isEmpty()) {
                val value = tokenType.regex.find(processed)?.value!!
                if (token is Token.String) {
                    token.value = value.drop(1).dropLast(1)
                } else
                    token.value = value
            }

            tokens += token

            char += range.last
            processed = processed.substring(range.last+1)
            processed = processed.trimStart {
                char++
                it.isWhitespace() && it !in arrayOf('\n', '\r')
            }
        }

        return tokens
    }
}
