package lang.aurum.parsing.stages

data class Symbol (
    val symbol: String,
    val alias: String = symbol.split(".").last()
)