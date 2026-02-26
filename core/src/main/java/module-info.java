module aurum.core {
    exports lang.aurum;
    exports lang.aurum.util;
    exports lang.aurum.model;
    exports lang.aurum.model.util;
    exports lang.aurum.model.impl to aurum.parsing;
    exports lang.aurum.model.factory to aurum.parsing;

    opens lang.aurum;
    opens lang.aurum.util;
    opens lang.aurum.model;

    requires org.jetbrains.annotations;
    requires kotlin.stdlib;
    requires kotlin.reflect;
}