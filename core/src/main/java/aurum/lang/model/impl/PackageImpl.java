package aurum.lang.model.impl;

import aurum.lang.model.Member;
import aurum.lang.model.Package;
import aurum.lang.model.Type;

public record PackageImpl (
        String name,
        Package parent,
        Type[] publicTypes,
        Type[] privateTypes,
        Member[] publicMembers,
        Member[] privateMembers,
        Package[] publicPackages,
        Package[] privatePackages
) implements Package {}
