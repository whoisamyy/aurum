module aurum.cli {
    exports aurum.lang.cli.args;

    opens aurum.lang.cli;

    requires aurum.compiler;
    requires kotlin.stdlib;
    requires org.jetbrains.annotations;
}