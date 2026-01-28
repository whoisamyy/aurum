module aurum.parsing {
    exports lang.aurum.parsing.attribute;
    exports lang.aurum.parsing.model;

    exports lang.aurum.parsing;
    exports lang.aurum.parsing.stages.optimisation to aurum.cli;

    opens lang.aurum.parsing;
    opens lang.aurum.parsing.stages.optimisation;

    requires aurum.ir;
    requires aurum.core;

    requires org.antlr.antlr4.runtime;
    requires org.jetbrains.annotations;
    requires kotlin.stdlib;
}