package com.telenav.vertx.guice.util;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Collector for indirect and direct instantiation strategies for a type, which
 * can resolve them all into a list of Provider instances given a binder.
 * Non-API.
 *
 * @author Tim Boudreau
 * @param <T> The type
 */
public class TypeOrInstanceList<T> {

    private final List<Entry<T>> entries = new ArrayList<>();

    TypeOrInstanceList() {
        // do nothing - package private to prevent subclassing outside
        // this package
    }

    public static <T> TypeOrInstanceList<T> typeOrInstanceList() {
        return new TypeOrInstanceList<>();
    }

    public TypeOrInstanceList<T> add(T instance) {
        entries.add(new InstanceEntry<>(instance));
        return this;
    }

    public TypeOrInstanceList<T> add(Class<? extends T> type) {
        entries.add(new TypeEntry<>(type));
        return this;
    }

    public TypeOrInstanceList<T> addProvider(Provider<T> type) {
        entries.add(new ProviderEntry<>(type));
        return this;
    }

    public TypeOrInstanceList<T> add(Function<? super Binder, Provider<? extends T>> func) {
        entries.add(func::apply);
        return this;
    }

    public List<Provider<? extends T>> get(Binder binder) {
        List<Provider<? extends T>> providers = new ArrayList<>();
        for (Entry<T> en : entries) {
            providers.add(en.toProvider(binder));
        }
        return providers;
    }

    interface Entry<T> {

        Provider<? extends T> toProvider(Binder binder);
    }

    private static final class TypeEntry<T> implements Entry<T> {

        private final Class<? extends T> type;

        public TypeEntry(Class<? extends T> type) {
            this.type = type;
        }

        @Override
        public Provider<? extends T> toProvider(Binder binder) {
            return binder.getProvider(type);
        }
    }

    private static final class InstanceEntry<T> implements Entry<T> {

        private final T instance;

        public InstanceEntry(T instance) {
            this.instance = instance;
        }

        @Override
        public Provider<? extends T> toProvider(Binder binder) {
            return Providers.of(instance);
        }
    }

    private static final class ProviderEntry<T> implements Entry<T> {

        private final Provider<T> provider;

        public ProviderEntry(Provider<T> provider) {
            this.provider = provider;
        }

        @Override
        public Provider<? extends T> toProvider(Binder binder) {
            return provider;
        }

    }
}
