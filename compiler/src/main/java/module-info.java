module aurum.compiler {
    exports aurum.lang.compiler.frontend.attribute;
    exports aurum.lang.compiler.frontend.model;

    exports aurum.lang.compiler.backend.jvm;

    exports aurum.lang.compiler.backend;

    opens aurum.lang.compiler.frontend;
    exports aurum.lang.compiler.frontend;
    exports aurum.lang.compiler.frontend.stages;
    exports aurum.lang.compiler.frontend.stages.parsing;
    exports aurum.lang.compiler.frontend.stages.analyzing;
    exports aurum.lang.compiler.frontend.stages.compiling;
    exports aurum.lang.compiler.frontend.stages.output;
    exports aurum.lang.compiler.frontend.stages.typeresolving;
    exports aurum.lang.compiler.frontend.stages.linking;
    exports aurum.lang.compiler.frontend.stages.optimization;
    exports aurum.lang.compiler.frontend.stages.translating;

    requires aurum.ir;
    requires aurum.core;
    requires aurum.runtime;

    requires org.jetbrains.annotations;
    requires kotlin.reflect;
    requires kotlin.stdlib;
}