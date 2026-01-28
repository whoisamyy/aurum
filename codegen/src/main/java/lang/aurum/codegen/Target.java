package lang.aurum.codegen;

import lang.aurum.Argument;

public enum Target implements Argument {
    JVM(".class"), AVM(".aur"), IR(".aur");

    public final String extension;

    Target(String extension) {
        this.extension = extension;
    }
}
