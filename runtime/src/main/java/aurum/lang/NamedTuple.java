package aurum.lang;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// Named tuple represents an expression like this: `(role: "User", age: 20, name: "Alex")`
// and acts like an immutable map with String as it's key type
// this implementation is just a wrapper on a HashMap
public class NamedTuple<T> implements Map<String, T> {
    private final Map<String, T> map;

    @SafeVarargs
    public NamedTuple(Pair<String, T>... pairs) {
        map = new HashMap<>(pairs.length, 1);
        for (var p : pairs) {
            map.put(p.a(), p.b());
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public T get(Object key) {
        return map.get(key);
    }

    @Override
    public @Nullable T put(String key, T value) {
        throw new IllegalStateException("Cannot add values to named tuples");
    }

    @Override
    public T remove(Object key) {
        throw new IllegalStateException("Cannot remove values from named tuples");
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends T> m) {
        throw new IllegalStateException("Cannot add values to named tuples");
    }

    @Override
    public void clear() {
        throw new IllegalStateException("Cannot remove values from named tuples");
    }

    @Override
    public @NotNull Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public @NotNull Collection<T> values() {
        return map.values();
    }

    @Override
    public @NotNull Set<Entry<String, T>> entrySet() {
        return map.entrySet();
    }
}
