/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.vertx.guice.scope;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * A straightforward, reentrant Guice scope. Supports binding concrete types and
 * Optionals only - no type parameters other than those on Optional, or
 * annotations, since it is essentially a bag of objects and neither type
 * parameters nor annotations will be visible on objects placed into the scope
 * by callers.
 *
 * @author Tim Boudreau
 */
public final class RequestScope implements Scope {

    private final LinkedList<List<Object>> contents = new LinkedList<>();

    @Override
    @SuppressWarnings({"unchecked", "rawType"})
    public <T> Provider<T> scope(Key<T> key, Provider<T> prvdr) {
        checkKey(key);
        if (key.getTypeLiteral().getRawType() == Optional.class) {
            Key k = (Key<Optional<?>>) key;
            return optionalProvider(k);
        }
        return new P<>(key, prvdr);
    }

    private <T> void checkKey(Key<T> key) {
        if (key.getAnnotation() != null || key.getAnnotationType() != null) {
            throw new IllegalArgumentException("RequestScope supports unannotated bindings only");
        }
        TypeLiteral<T> lit = key.getTypeLiteral();
        if (!lit.getType().getTypeName().equals(lit.getRawType().getName())) {
            // FIXME:  We can support exactly ONE Optional in scope without doing
            // some special handling just for that - needs a separate Provider class
            // for Optional which checks the value of get() for any Optionals, and if
            // none
            if (lit.getRawType() != Optional.class) {
                throw new IllegalArgumentException("RequestScope does not support injecting parameterized types");
            }
        }
    }

    @Override
    public String toString() {
        return "RequestScope(" + contents + ")";
    }

    /**
     * Enter this scope with some objects that should be injectable.
     *
     * @param with Some objects
     * @return An AutoCloseable which removes the passed objects from the scope
     */
    public ScopeEntry enter(Object... with) {
        if (with.length == 0) {
            return ScopeEntry.NO_OP;
        }
        return enter(asList(with));
    }

    /**
     * Enter this scope with some objects that should be injectable.
     *
     * @param with Some objects
     * @return An AutoCloseable which removes the passed objects from the scope
     */
    public ScopeEntry enter(Collection<? extends Object> with) {
        if (with.isEmpty()) {
            return ScopeEntry.NO_OP;
        }
        LinkedList<Object> newContents = new LinkedList<>();
        for (Object o : with) {
            newContents.add(o);
        }
        contents.addFirst(newContents);
        return () -> contents.removeFirst();
    }

    /**
     * Bind a collection of concrete types.
     *
     * @param binder A binder
     * @param types Some types - must be concrete types
     */
    public void bindTypes(Binder binder, Class<?>... types) {
        for (Class<?> type : types) {
            bindType(binder, type);
        }
    }

    /**
     * Bind one type.
     *
     * @param <T> The type
     * @param binder a binder
     * @param type a type
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T> RequestScope bindType(Binder binder, Class<T> type) {
        binder.bind(type).toProvider(scope(Key.get(type),
                (Provider<T>) NotInScopeProvider.INSTANCE))
                .in(this);
        return this;
    }

    /**
     * Bind one type, using a custom fallback provider.
     *
     * @param <T> The type
     * @param binder A binder
     * @param type A type
     * @param fallback The fallback provider
     * @return this
     */
    public <T> RequestScope bindType(Binder binder, Class<T> type, Provider<T> fallback) {
        binder.bind(type).toProvider(scope(Key.get(type), fallback)).in(this);
        return this;
    }

    /**
     * Bind <code>Optional&lt;T&gt;</code> - the type must be a concrete type
     * with no type paramaters.
     *
     * @param <T> The type
     * @param binder A binder
     * @param type A type
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <T> RequestScope bindOptional(Binder binder, Class<T> type) {
        Key<Optional<T>> key = (Key<Optional<T>>) Key.get(new FakeOptionalType<>(type));
//        binder.bind(key).toProvider(new P<>(key, Providers.of(Optional.empty()))).in(this);
        binder.bind(key).toProvider(new OP(key)).in(this);
        return this;
    }

    /**
     * Run the passed runnable with the scope contents being its current
     * contents and the passed objects.
     *
     * @param run A runnable
     * @param objs Some objects
     */
    public void run(Runnable run, Object... objs) {
        try (ScopeEntry e = enter(objs)) {
            run.run();
        }
    }

    /**
     * Run the passed runnable with the scope contents being its current
     * contents and the passed objects.
     *
     * @param run A runnable
     * @param objs Some objects
     */
    public void run(List<Object> objs, Runnable run) {
        try (ScopeEntry e = enter(objs)) {
            run.run();
        }
    }

    /**
     * Wrap the passed runnable in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents before running it.
     *
     * @param run A runnable
     * @return A wrapper for that runnable
     */
    public Runnable wrap(Runnable run) {
        if (contents.isEmpty()) {
            return run;
        }
        Snapshot snap = new Snapshot();
        return snap.wrap(run);
    }

    /**
     * Wrap the passed runnable in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents, adding the passed
     * objects before running it.
     *
     * @param run A runnable
     * @param add Some objects to add
     * @return A wrapper for that runnable
     */
    public Runnable wrap(Runnable run, Object... add) {
        try (ScopeEntry en = enter(add)) {
            Snapshot snap = new Snapshot(asList(add));
            return snap.wrap(() -> {
                run.run();
            });
        }
    }

    /**
     * Wrap the passed runnable in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents, adding the passed
     * objects before running it.
     *
     * @param run A runnable
     * @param add Some objects to add
     * @return A wrapper for that runnable
     */
    public Runnable wrap(Collection<? extends Object> add, Runnable run) {
        try (ScopeEntry en = enter(add)) {
            if (contents.isEmpty()) {
                return run;
            }
            return new Snapshot(add).wrap(run);
        }
    }

    /**
     * Wrap the passed runnable in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents before running it.
     *
     * @param run A runnable
     * @return A wrapper for that runnable
     */
    public Handler<RoutingContext> wrap(Handler<RoutingContext> run) {
        return new Snapshot().wrap(run);
    }

    /**
     * Wrap the passed handler in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents, adding the passed
     * objects before running it.
     *
     * @param run A runnable
     * @param add Some objects to add
     * @return A wrapper for that runnable
     */
    public Handler<RoutingContext> wrap(Handler<RoutingContext> run, Object... objs) {
        return new Snapshot(asList(objs)).wrap(run);
    }

    /**
     * Wrap the passed handler in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents, adding the passed
     * objects before running it.
     *
     * @param run A runnable
     * @param add Some objects to add
     * @return A wrapper for that runnable
     */
    public Handler<RoutingContext> wrap(Collection<? extends Object> objs,
            Handler<RoutingContext> run) {
        return new Snapshot(objs).wrap(run);
    }

    // Used by tests
    List<Object> contents() {
        List<Object> result = new ArrayList<>();
        contents.forEach(result::addAll);
        return result;
    }

    boolean inScope() {
        return !contents.isEmpty();
    }

    <T> Provider<Optional<T>> optionalProvider(Key<Optional<T>> key) {
        return new OP<>(key);
    }

    class OP<T> implements Provider<Optional<T>> {

        private final Key<Optional<T>> key;
        private final Class<T> tType;

        @SuppressWarnings("unchecked")
        OP(Key<Optional<T>> key) {
            this.key = key;
            Type t = key.getTypeLiteral().getType();
            if (!(t instanceof ParameterizedType)) {
                throw new IllegalStateException("Binding is not for a parameterized type: " + key);
            }
            ParameterizedType pt = (ParameterizedType) key.getTypeLiteral().getType();
            Type[] params = pt.getActualTypeArguments();
            if (params.length != 1) {
                throw new IllegalStateException("Wrong number of type arguments: " + asList(params));
            }
            tType = (Class<T>) params[0];
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<T> get() {
            for (List<Object> l : contents) {
                for (Object o : l) {
                    if (o instanceof Optional<?>) {
                        Optional<?> opt = (Optional<?>) o;
                        if (!opt.isPresent()) {
                            continue;
                        }
                        Object target = opt.get();
                        if (tType.isInstance(target)) {
                            return opt.map(t -> (T) t);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    class P<T> implements Provider<T> {

        private final Key<T> key;
        private final Provider<T> fallback;

        P(Key<T> key, Provider<T> fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T get() {
            Class<? super T> type = key.getTypeLiteral().getRawType();
            for (List<Object> l : contents) {
                for (Object o : l) {
                    if (type.isInstance(o)) {
                        return (T) type.cast(o);
                    }
                }
            }
            return fallback.get();
        }
    }

    final class Snapshot {

        private final LinkedList<List<Object>> snapshot;

        Snapshot() {
            this.snapshot = new LinkedList<>();
            for (List<Object> l : contents) {
                LinkedList<Object> copy = new LinkedList<>(l);
                snapshot.add(copy);
            }
        }

        Snapshot(Collection<? extends Object> o) {
            this();
            this.snapshot.add(new ArrayList<>(o));
        }

        Runnable wrap(Runnable r) {
            return () -> {
                try (ScopeEntry en = enter()) {
                    r.run();
                }
            };
        }

        Handler<RoutingContext> wrap(Handler<RoutingContext> h) {
            return ctx -> {
                try (ScopeEntry en = enter()) {
                    h.handle(ctx);
                }
            };
        }

        public ScopeEntry enter() {
            SnapshotEntry result = new SnapshotEntry();
            contents.clear();
            contents.addAll(snapshot);
            return result;
        }

        class SnapshotEntry implements ScopeEntry {

            private final List<List<Object>> originalContents;

            SnapshotEntry() {
                originalContents = new ArrayList<>(contents);
            }

            @Override
            public void close() {
                contents.clear();
                contents.addAll(originalContents);
            }
        }
    }

    static final class NotInScopeProvider implements Provider<Object> {

        private static final NotInScopeProvider INSTANCE = new NotInScopeProvider();

        @Override
        public Object get() {
            throw new IllegalStateException("Not in scope");
        }
    }

}
