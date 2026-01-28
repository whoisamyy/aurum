module aurum.cli {
    opens lang.aurum.cli;

    requires aurum.parsing;
    requires aurum.core;
    requires aurum.ir;
    requires aurum.codegen;
    requires kotlin.stdlib;
    requires org.antlr.antlr4.runtime;
    requires org.jetbrains.annotations;
}