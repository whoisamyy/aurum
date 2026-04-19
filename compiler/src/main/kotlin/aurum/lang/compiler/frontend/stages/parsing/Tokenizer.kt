package aurum.lang.compiler.frontend.stages.parsing

import aurum.lang.compiler.frontend.stages.TokenList

class Tokenizer (
    val input: String
) {
    fun parse(): TokenList {
        val tokens = mutableListOf<Token>()
        input.split("\n").forEachIndexed { i, s ->
            tokens += parseLine(s, i+1)
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

            if (token.value.isEmpty())
                token.value = tokenType.regex.find(processed)?.value!!

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
