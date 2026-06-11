package aurum.lang.cli.args;

public enum Target implements Argument {
    JVM(".class"), IRB(".aub"), IR(".aur"), INTERPRET(".class");

    public final String extension;

    Target(String extension) {
        this.extension = extension;
    }
}
