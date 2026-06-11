module aurum.core {
    exports aurum.lang.util;
    exports aurum.lang.model;
    exports aurum.lang.model.util;
    exports aurum.lang.model.attribute;
    exports aurum.lang.model.impl to aurum.compiler;
    exports aurum.lang.model.factory to aurum.compiler;

    opens aurum.lang.util;
    opens aurum.lang.model;

    requires kotlin.stdlib;
    requires org.jetbrains.annotations;
}