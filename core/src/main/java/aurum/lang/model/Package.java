package aurum.lang.model;

import aurum.lang.model.impl.PackageImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public interface Package {
    @NotNull String name();
    Package parent();

    @NotNull Type @NotNull [] publicTypes();
    @NotNull Type @NotNull [] privateTypes();

    @NotNull Member @NotNull [] publicMembers();
    @NotNull Member @NotNull [] privateMembers();

    @NotNull Package @NotNull [] publicPackages();
    @NotNull Package @NotNull [] privatePackages();

    default @NotNull Type @NotNull [] types() {
        Type[] allTypes = new Type[publicTypes().length + privateTypes().length];
        Arrays.asList(allTypes).addAll(Arrays.asList(publicTypes()));
        Arrays.asList(allTypes).addAll(Arrays.asList(privateTypes()));

        return allTypes;
    }

    default @NotNull Member @NotNull [] members() {
        Member[] allMembers = new Member[publicMembers().length + privateMembers().length];
        Arrays.asList(allMembers).addAll(Arrays.asList(publicMembers()));
        Arrays.asList(allMembers).addAll(Arrays.asList(privateMembers()));

        return allMembers;
    }

    default @NotNull Package @NotNull [] packages() {
        Package[] allPackages = new Package[publicPackages().length + privatePackages().length];
        Arrays.asList(allPackages).addAll(Arrays.asList(publicPackages()));
        Arrays.asList(allPackages).addAll(Arrays.asList(privatePackages()));

        return allPackages;
    }

    static @NotNull Package of(
            String name,
            Package parent,
            Type[] publicTypes,
            Type[] privateTypes,
            Member[] publicMembers,
            Member[] privateMembers,
            Package[] publicPackages,
            Package[] privatePackages
    ) {
        return new PackageImpl(
                name, parent,
                publicTypes,
                privateTypes,
                publicMembers,
                privateMembers,
                publicPackages,
                privatePackages
        );
    }
}
