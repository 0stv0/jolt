package stevku.jolt.domain;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Manager<K, V> implements Iterable<V> {

    private volatile Map<K, V> items =
        new ConcurrentHashMap<>();

    public V get(K index)
    {
        return this.items.get(index);
    }
    public void set(K key, V value)
    {
        this.items.put(key, value);
    }
    public void remove(K key)
    {
        this.items.remove(key);
    }
    public boolean has(K key)
    {
        return this.items.containsKey(key);
    }
    public int size()
    {
        return this.items.size();
    }
    @Override
    public @Nonnull Iterator<V> iterator()
    {
        return this.items.
            values().
            iterator();
    }
}
