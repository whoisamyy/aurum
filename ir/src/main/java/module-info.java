/// Intermediate representation of Aurum's classes, functions etc.
/// Allows creation of multiple compiling targets and optimizations before compiling
module aurum.ir {
    exports aurum.lang.ir;
    exports aurum.lang.attribute;

    requires transitive aurum.core;

    requires kotlin.stdlib;
}