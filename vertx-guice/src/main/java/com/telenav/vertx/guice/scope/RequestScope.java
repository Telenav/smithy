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

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * A straightforward, reentrant Guice scope.
 *
 * @author Tim Boudreau
 */
public final class RequestScope implements Scope {

    private final LinkedList<List<Object>> contents = new LinkedList<>();

    @Override
    public <T> Provider<T> scope(Key<T> key, Provider<T> prvdr) {
        checkKey(key);
        return new P<>(key, prvdr);
    }

    private <T> void checkKey(Key<T> key) {
        if (key.getAnnotation() != null || key.getAnnotationType() != null) {
            throw new IllegalArgumentException("RequestScope supports unannotated bindings only");
        }
        if (!key.getTypeLiteral().getType().getTypeName().equals(key.getTypeLiteral().getRawType().getName())) {
            throw new IllegalArgumentException("RequestScope does not support injecting parameterized types");
        }
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
            newContents.addFirst(o);
        }
        return () -> contents.removeFirst();
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
            if (contents.isEmpty()) {
                return run;
            }
            Snapshot snap = new Snapshot(asList(add));
            return snap.wrap(run);
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
            Snapshot snap = new Snapshot(add);
            return snap.wrap(run);
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
}
