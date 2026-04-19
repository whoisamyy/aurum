module aurum.cli {
    opens aurum.lang.cli;

    requires aurum.compiler;
    requires aurum.core;
    requires aurum.ir;
    requires kotlin.stdlib;
    requires org.antlr.antlr4.runtime;
    requires org.jetbrains.annotations;
}