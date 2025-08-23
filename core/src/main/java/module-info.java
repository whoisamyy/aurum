module aurum.core {
    requires aurum.core;
    exports lang.aurum.model;
    exports lang.aurum.model.impl to aurum.parsing;
}