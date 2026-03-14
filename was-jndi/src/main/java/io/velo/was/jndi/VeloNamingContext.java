package io.velo.was.jndi;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple in-memory JNDI naming context.
 * Supports hierarchical namespaces using "/" as separator.
 * <p>
 * Usage example:
 * <pre>
 *   VeloNamingContext root = new VeloNamingContext();
 *   root.bind("java:comp/env/jdbc/myDB", dataSource);
 *   DataSource ds = (DataSource) root.lookup("java:comp/env/jdbc/myDB");
 * </pre>
 */
public class VeloNamingContext implements Context {

    private final Map<String, Object> bindings = new ConcurrentHashMap<>();
    private final String prefix;
    private final Hashtable<String, Object> environment;

    public VeloNamingContext() {
        this("", new Hashtable<>());
    }

    VeloNamingContext(String prefix, Hashtable<String, Object> environment) {
        this.prefix = prefix;
        this.environment = new Hashtable<>(environment);
    }

    @Override
    public Object lookup(Name name) throws NamingException {
        return lookup(name.toString());
    }

    @Override
    public Object lookup(String name) throws NamingException {
        String key = resolveKey(name);

        // Direct lookup
        Object value = bindings.get(key);
        if (value != null) {
            return value;
        }

        // Check if it's a sub-context (prefix matches)
        String subPrefix = key.endsWith("/") ? key : key + "/";
        boolean hasChildren = bindings.keySet().stream().anyMatch(k -> k.startsWith(subPrefix));
        if (hasChildren) {
            return new VeloNamingContext(subPrefix, environment);
        }

        throw new NameNotFoundException("Name not found: " + name);
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
        bind(name.toString(), obj);
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        String key = resolveKey(name);
        if (bindings.containsKey(key)) {
            throw new NamingException("Name already bound: " + name);
        }
        bindings.put(key, obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        rebind(name.toString(), obj);
    }

    @Override
    public void rebind(String name, Object obj) {
        bindings.put(resolveKey(name), obj);
    }

    @Override
    public void unbind(Name name) throws NamingException {
        unbind(name.toString());
    }

    @Override
    public void unbind(String name) {
        bindings.remove(resolveKey(name));
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
        rename(oldName.toString(), newName.toString());
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
        Object value = lookup(oldName);
        unbind(oldName);
        bind(newName, value);
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        return list(name.toString());
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        String subPrefix = resolveKey(name);
        if (!subPrefix.isEmpty() && !subPrefix.endsWith("/")) {
            subPrefix = subPrefix + "/";
        }
        String finalPrefix = subPrefix;
        var pairs = bindings.entrySet().stream()
                .filter(e -> e.getKey().startsWith(finalPrefix))
                .map(e -> new NameClassPair(
                        e.getKey().substring(finalPrefix.length()),
                        e.getValue().getClass().getName()))
                .toList();
        return new SimpleNamingEnumeration<>(pairs);
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return listBindings(name.toString());
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        String subPrefix = resolveKey(name);
        if (!subPrefix.isEmpty() && !subPrefix.endsWith("/")) {
            subPrefix = subPrefix + "/";
        }
        String finalPrefix = subPrefix;
        var binds = bindings.entrySet().stream()
                .filter(e -> e.getKey().startsWith(finalPrefix))
                .map(e -> new Binding(
                        e.getKey().substring(finalPrefix.length()),
                        e.getValue()))
                .toList();
        return new SimpleNamingEnumeration<>(binds);
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        return createSubcontext(name.toString());
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        String key = resolveKey(name);
        VeloNamingContext sub = new VeloNamingContext(key.endsWith("/") ? key : key + "/", environment);
        bindings.put(key, sub);
        return sub;
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
        destroySubcontext(name.toString());
    }

    @Override
    public void destroySubcontext(String name) {
        String subPrefix = resolveKey(name);
        if (!subPrefix.endsWith("/")) {
            subPrefix = subPrefix + "/";
        }
        String finalPrefix = subPrefix;
        bindings.entrySet().removeIf(e -> e.getKey().startsWith(finalPrefix));
        bindings.remove(resolveKey(name));
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return lookup(name);
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return lookup(name);
    }

    @Override
    public NameParser getNameParser(Name name) {
        return VeloNameParser.INSTANCE;
    }

    @Override
    public NameParser getNameParser(String name) {
        return VeloNameParser.INSTANCE;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        throw new OperationNotSupportedException("composeName not supported");
    }

    @Override
    public String composeName(String name, String prefix) {
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) {
        return environment.put(propName, propVal);
    }

    @Override
    public Object removeFromEnvironment(String propName) {
        return environment.remove(propName);
    }

    @Override
    public Hashtable<?, ?> getEnvironment() {
        return new Hashtable<>(environment);
    }

    @Override
    public String getNameInNamespace() {
        return prefix;
    }

    @Override
    public void close() {
        // no-op
    }

    /** Direct access to the internal bindings — useful for admin commands. */
    public Map<String, Object> allBindings() {
        return Collections.unmodifiableMap(bindings);
    }

    private String resolveKey(String name) {
        if (prefix.isEmpty()) {
            return name;
        }
        if (name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

    /**
     * Simple NamingEnumeration backed by a list.
     */
    private static class SimpleNamingEnumeration<T> implements NamingEnumeration<T> {
        private final java.util.Iterator<T> iterator;

        SimpleNamingEnumeration(java.util.List<T> list) {
            this.iterator = list.iterator();
        }

        @Override
        public T next() {
            return iterator.next();
        }

        @Override
        public boolean hasMore() {
            return iterator.hasNext();
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasMoreElements() {
            return hasMore();
        }

        @Override
        public T nextElement() {
            return next();
        }
    }
}
