package lang.aurum.parsing

import lang.aurum.Argument
import lang.aurum.Option

sealed interface ParserArgument : Argument {
    @Option(names = ["-ir", "--generate-ir"])
    object GenerateIRFiles : ParserArgument
    @Option(names = ["-v", "--verbose-ir"])
    object VerboseIR : ParserArgument
    @Option(names = ["-S", "--print-stacktrace"])
    object PrintStackTrace : ParserArgument
}