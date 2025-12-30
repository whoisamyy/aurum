package lang.aurum.util;

import lang.aurum.Argument;

import java.util.HashSet;

public class ArgumentSet extends HashSet<Argument> {
    @Override
    public boolean contains(Object o) {
        if (this.stream().anyMatch(el -> o.getClass().isInstance(el)))
            return true;

        return super.contains(o);
    }
}
