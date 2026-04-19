package aurum.lang.cli.args;

public enum Target implements Argument {
    JVM(".class"), AVM(".aub"), IR(".aur");

    public final String extension;

    Target(String extension) {
        this.extension = extension;
    }
}
