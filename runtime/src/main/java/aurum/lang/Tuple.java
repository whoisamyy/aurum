package aurum.lang;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

// The real problem with Tuples is that they need vararg type parameters
// (e.g. Tuple<int, int> or Tuple<String, Int, Float>)
// This implementation is just an immutable array, actual Tuple typing happens at compile time or in AOT compilation
public class Tuple<T> implements Iterable<T>, Collection<T> {
    private static final Object[] emptyArray = new Object[0];
    public static final Tuple<?> emptyTuple = new Tuple<>(emptyArray);

    @SuppressWarnings("unchecked")
    public static <T> Tuple<T> getEmptyTuple() {
        return (Tuple<T>) emptyTuple;
    }

    private final T[] values;

    @SafeVarargs
    public Tuple(T... values) {
        this.values = values;
    }

    @Override
    public int size() {
        return values.length;
    }

    @Override
    public boolean isEmpty() {
        return values.length == 0;
    }

    @Override
    public boolean contains(Object o) {
        return Set.of(values).contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return new TupleIterator();
    }

    private class TupleIterator implements Iterator<T> {
        private int index = 0;

        @Override
        public boolean hasNext() {
            return index < values.length-1;
        }

        @Override
        public T next() {
            return values[index++];
        }
    }

    @Override
    public Object[] toArray() {
        return values.clone();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NotNull
    public <T1> T1[] toArray(T1[] a) {
        if (a.length == 0)
            return ((T1[]) values.clone());

        System.arraycopy(values, 0, a, 0, values.length);

        return a;
    }

    @Override
    public boolean add(T t) {
        throw new IllegalStateException("Cannot add elements to tuple");
    }

    @Override
    public boolean remove(Object o) {
        throw new IllegalStateException("Cannot remove elements from tuple");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new IllegalStateException("Cannot add elements to tuple");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new IllegalStateException("Cannot remove elements from tuple");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new IllegalStateException("Cannot remove elements from tuple");
    }

    @Override
    public void clear() {
        throw new IllegalStateException("Cannot remove elements from tuple");
    }
}
