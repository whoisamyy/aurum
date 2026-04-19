module aurum.compiler {
    exports aurum.lang.compiler.frontend.attribute;
    exports aurum.lang.compiler.frontend.model;

    exports aurum.lang.compiler.backend.jvm to aurum.cli;

//    exports aurum.lang.compiler.frontend.stages.optimisation to aurum.cli;

    exports aurum.lang.compiler.backend;

    opens aurum.lang.compiler.frontend;
    opens aurum.lang.compiler.frontend.stages.optimisation;
    exports aurum.lang.compiler.frontend;
    exports aurum.lang.compiler.frontend.stages;
    exports aurum.lang.compiler.frontend.stages.parsing;
    exports aurum.lang.compiler.frontend.stages.analyzing;

    requires aurum.ir;
    requires aurum.core;

    requires org.antlr.antlr4.runtime;
    requires org.jetbrains.annotations;
    requires kotlin.stdlib;
}