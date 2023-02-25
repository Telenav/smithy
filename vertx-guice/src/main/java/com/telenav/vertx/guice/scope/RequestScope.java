/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.vertx.guice.scope;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.ParsedHeaderValues;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.impl.RoutingContextInternal;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private final ThreadLocal<LinkedList<List<Object>>> contents = ThreadLocal.withInitial(NullIntolerantList::new);

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
        return "RequestScope(" + contents() + ")";
    }

    LinkedList<List<Object>> contents() {
        return contents.get();
    }

    List<Object> objects() {
        List<Object> result = new ArrayList<>();
        for (List<Object> l : contents()) {
            result.addAll(l);
        }
        return result;
    }

    /**
     * For purposes such as error logging, it may be necessary to get an object
     * out of the scope, such as a requeest ID, if one is present, without
     * throwing an exception as a provider would.
     *
     * @param <T> The type
     * @param type The type
     * @return An optional containing the first match in-scope, if there is one
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSafe(Class<T> type) {
        for (List<Object> l : contents()) {
            for (Object o : l) {
                if (type.isInstance(o)) {
                    return Optional.of(type.cast(o));
                } else if (o instanceof Optional<?>) {
                    if (((Optional<?>) ((Optional<?>) o)).isPresent()) {
                        Object val = ((Optional<?>) o).get();
                        if (type.isInstance(val)) {
                            return (Optional<T>) o;
                        }
                    }
                }
            }
        }
        return Optional.empty();
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
        newContents.addAll(with);
        LinkedList<List<Object>> l = contents();
        l.addFirst(newContents);
        return () -> l.removeFirst();
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
        if (type == null) {
            throw new IllegalArgumentException("Null type");
        }
        if (binder == null) {
            throw new IllegalArgumentException("Null binder");
        }
        Provider<T> fallback = (Provider<T>) NotInScopeProvider.INSTANCE;
        Provider<T> scopedProvider = scope(Key.get(type), fallback);
        binder.bind(type).toProvider(scopedProvider)
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
        if (contents().isEmpty()) {
            return run;
        }
        Snapshot snap = new Snapshot();
        return snap.wrap(run);
    }

    /**
     * Wrap a handler so that scope contents are available.
     *
     * @param supp A supplier for the handler
     * @return A handler which invokes the supplier on demand
     */
    public Handler<RoutingContext> wrap(Supplier<? extends Handler<RoutingContext>> supp) {
        return new WrappedHandler(this, supp);
    }

    /**
     * Wrap the passed runnable in one which can be run asynchronously and will
     * reconstitute a snapshot of the current scope contents before running it.
     *
     * @param run A runnable
     * @return A wrapper for that runnable
     */
    public Runnable wrap(Runnable run, Consumer<? super Throwable> onError, Object... toAdd) {
        Snapshot snap = new Snapshot(asList(toAdd));
        return snap.wrap(run, onError);
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
            if (contents().isEmpty()) {
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
    List<Object> contentsCopy() {
        List<Object> result = new ArrayList<>();
        contents().forEach(result::addAll);
        return result;
    }

    boolean inScope() {
        return !contents().isEmpty();
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
            for (List<Object> l : contents()) {
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
            for (List<Object> l : contents()) {
                if (l == null) {
                    continue;
                }
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
            for (List<Object> l : contents()) {
                if (l != null) {
                    LinkedList<Object> copy = new LinkedList<>(l);
                    snapshot.add(copy);
                }
            }
        }

        @Override
        public String toString() {
            return "Snapshot(" + snapshot + ")";
        }

        Snapshot(Collection<? extends Object> o) {
            this();
            this.snapshot.add(new ArrayList<>(o));
        }

        Runnable wrap(Runnable r) {
            ClassLoader ldr = Thread.currentThread().getContextClassLoader();
            return () -> {
                Thread t = Thread.currentThread();
                ClassLoader old = t.getContextClassLoader();
                t.setContextClassLoader(ldr);
                try (ScopeEntry en = enter()) {
                    r.run();
                } finally {
                    t.setContextClassLoader(old);
                }
            };
        }

        Runnable wrap(Runnable r, Consumer<? super Throwable> onError) {
            ClassLoader ldr = Thread.currentThread().getContextClassLoader();
            Exception debug = new Exception("wrap");
            return () -> {
                Thread t = Thread.currentThread();
                ClassLoader old = t.getContextClassLoader();
                t.setContextClassLoader(ldr);
                try (ScopeEntry en = enter()) {
                    r.run();
                } catch (Exception | Error e) {
                    e.addSuppressed(debug);
                    onError.accept(e);
                } finally {
                    t.setContextClassLoader(old);
                }
            };
        }

        Handler<RoutingContext> wrap(Supplier<Handler<RoutingContext>> h) {
            return new WrappedHandler(RequestScope.this, h);
        }

        Handler<RoutingContext> wrap(Handler<RoutingContext> h) {
            return new WrappedHandler(RequestScope.this, new IdentitySupplier<>(h));
//            ClassLoader ldr = Thread.currentThread().getContextClassLoader();
//            return ctx -> {
//                Thread t = Thread.currentThread();
//                ClassLoader old = t.getContextClassLoader();
//                t.setContextClassLoader(ldr);
//                try (ScopeEntry en = enter()) {
//                    h.handle(ctx);
//                } finally {
//                    t.setContextClassLoader(old);
//                }
//            };
        }

        RequestScope scope() {
            return RequestScope.this;
        }

        public ScopeEntry enter() {
            SnapshotEntry result = new SnapshotEntry();
            LinkedList<List<Object>> l = contents();
            l.clear();
            l.addAll(snapshot);
            return result;
        }

        class SnapshotEntry implements ScopeEntry {

            private final List<List<Object>> originalContents;

            SnapshotEntry() {
                originalContents = new ArrayList<>(contents());
            }

            @Override
            public void close() {
                LinkedList<List<Object>> l = contents();
                l.clear();
                l.addAll(originalContents);
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

    Snapshot snapshot() {
        return new Snapshot();
    }

    static class IdentitySupplier<T> implements Supplier<T> {

        private final T t;

        IdentitySupplier(T t) {
            this.t = t;
        }

        @Override
        public T get() {
            return t;
        }

        @Override
        public String toString() {
            return t.toString();
        }
    }

    public <T> Handler<T> wrapGeneral(Handler<T> h) {
        if (h instanceof GeneralWrappedHandler<?>) {
            return h;
        }
        return new GeneralWrappedHandler<>(this, h);
    }

    static class GeneralWrappedHandler<T> implements Handler<T> {

        private final Snapshot snapshot;
        private final Handler<T> orig;

        public GeneralWrappedHandler(RequestScope scope, Handler<T> orig) {
            this.snapshot = scope.snapshot();
            this.orig = orig;
        }

        @Override
        public void handle(T event) {
            try (ScopeEntry entry = snapshot.enter()) {
                orig.handle(event);
            }
        }
    }

    static class WrappedHandler implements Handler<RoutingContext> {

        private final RequestScope scope;
        private final Supplier<? extends Handler<RoutingContext>> orig;

        WrappedHandler(RequestScope scope, Supplier<? extends Handler<RoutingContext>> orig) {
            this.scope = scope;
            this.orig = orig;
        }

        @Override
        public void handle(RoutingContext event) {
            Snapshot snap = scope.snapshot();
            scope.run(() -> {
                orig.get().handle(new WrappedRoutingContext((RoutingContextInternal) event, snap));
            });
        }

        @Override
        public String toString() {
            return "WrappedHandler(" + orig + ")";
        }
    }

    static class WrappedRoutingContext implements RoutingContextInternal {

        private final RoutingContextInternal delegate;
        private final Snapshot snap;

        WrappedRoutingContext(RoutingContextInternal delegate, Snapshot snap) {
            this.delegate = delegate;
            this.snap = snap;
        }

        @Override
        public String toString() {
            return "Wrapped(" + delegate + ") with " + snap;
        }

        @Override
        public RoutingContextInternal visitHandler(int id) {
            return delegate.visitHandler(id);
        }

        @Override
        public boolean seenHandler(int id) {
            return delegate.seenHandler(id);
        }

        @Override
        public RoutingContextInternal setMatchFailure(int matchFailure) {
            return delegate.setMatchFailure(matchFailure);
        }

        @Override
        public Router currentRouter() {
            return delegate.currentRouter();
        }

        @Override
        public RoutingContextInternal parent() {
            return new WrappedRoutingContext(delegate.parent(), snap.scope().snapshot());
        }

        @Override
        @SuppressWarnings("deprecation") // can't not implement it
        public void setBody(Buffer buffer) {
            delegate.setBody(buffer);
        }

        @Override
        @SuppressWarnings("deprecation") // can't not implement it
        public void setSession(Session session) {
            delegate.setSession(session);
        }

        @Override
        public int restIndex() {
            return delegate.restIndex();
        }

        @Override
        public boolean normalizedMatch() {
            return delegate.normalizedMatch();
        }

        @Override
        public HttpServerRequest request() {
            return delegate.request();
        }

        @Override
        public HttpServerResponse response() {
            return delegate.response();
        }

        @Override
        public void next() {
            snap.wrap(delegate::next, this::fail).run();
        }

        @Override
        public void fail(int statusCode) {
            delegate.fail(statusCode);
        }

        @Override
        public void fail(Throwable throwable) {
            delegate.fail(throwable);
        }

        @Override
        public void fail(int statusCode, Throwable throwable) {
            delegate.fail(statusCode, throwable);
        }

        @Override
        public RoutingContext put(String key, Object obj) {
            delegate.put(key, obj);
            return this;
        }

        @Override
        public <T> T get(String key) {
            return delegate.get(key);
        }

        @Override
        public <T> T get(String key, T defaultValue) {
            return delegate.get(key, defaultValue);
        }

        @Override
        public <T> T remove(String key) {
            return delegate.remove(key);
        }

        @Override
        public Map<String, Object> data() {
            return delegate.data();
        }

        @Override
        public Vertx vertx() {
            return delegate.vertx();
        }

        @Override
        public String mountPoint() {
            return delegate.mountPoint();
        }

        @Override
        public Route currentRoute() {
            return delegate.currentRoute();
        }

        @Override
        public String normalizedPath() {
            return delegate.normalizedPath();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Cookie getCookie(String name) {
            return delegate.getCookie(name);
        }

        @Override
        @SuppressWarnings("deprecation")
        public RoutingContext addCookie(Cookie cookie) {
            delegate.addCookie(cookie);
            return this;
        }

        @Override
        @SuppressWarnings("deprecation")
        public Cookie removeCookie(String name, boolean invalidate) {
            return delegate.removeCookie(name, invalidate);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int cookieCount() {
            return delegate.cookieCount();
        }

        @Override
        @SuppressWarnings("deprecation")
        public Map<String, Cookie> cookieMap() {
            return delegate.cookieMap();
        }

        @Override
        public RequestBody body() {
            return delegate.body();
        }

        @Override
        public List<FileUpload> fileUploads() {
            return delegate.fileUploads();
        }

        @Override
        public Session session() {
            return delegate.session();
        }

        @Override
        public boolean isSessionAccessed() {
            return delegate.isSessionAccessed();
        }

        @Override
        public User user() {
            return delegate.user();
        }

        @Override
        public Throwable failure() {
            return delegate.failure();
        }

        @Override
        public int statusCode() {
            return delegate.statusCode();
        }

        @Override
        public String getAcceptableContentType() {
            return delegate.getAcceptableContentType();
        }

        @Override
        public ParsedHeaderValues parsedHeaders() {
            return delegate.parsedHeaders();
        }

        @Override
        public int addHeadersEndHandler(Handler<Void> handler) {
            return delegate.addHeadersEndHandler(handler);
        }

        @Override
        public boolean removeHeadersEndHandler(int handlerID) {
            return delegate.removeHeadersEndHandler(handlerID);
        }

        @Override
        public int addBodyEndHandler(Handler<Void> handler) {
            return delegate.addBodyEndHandler(handler);
        }

        @Override
        public boolean removeBodyEndHandler(int handlerID) {
            return delegate.removeBodyEndHandler(handlerID);
        }

        @Override
        public int addEndHandler(Handler<AsyncResult<Void>> handler) {
            return delegate.addEndHandler(handler);
        }

        @Override
        public boolean removeEndHandler(int handlerID) {
            return delegate.removeEndHandler(handlerID);
        }

        @Override
        public boolean failed() {
            return delegate.failed();
        }

        @Override
        public void setUser(User user) {
            delegate.setUser(user);
        }

        @Override
        public void clearUser() {
            delegate.clearUser();
        }

        @Override
        public void setAcceptableContentType(String contentType) {
            delegate.setAcceptableContentType(contentType);
        }

        @Override
        public void reroute(HttpMethod method, String path) {
            delegate.reroute(method, path);
        }

        @Override
        public Map<String, String> pathParams() {
            return delegate.pathParams();
        }

        @Override
        public String pathParam(String name) {
            return delegate.pathParam(name);
        }

        @Override
        public MultiMap queryParams() {
            return delegate.queryParams();
        }

        @Override
        public MultiMap queryParams(Charset encoding) {
            return delegate.queryParams(encoding);
        }

        @Override
        public List<String> queryParam(String name) {
            return delegate.queryParam(name);
        }
    }

    private static final class NullIntolerantList extends LinkedList<List<Object>> {

        @Override
        public boolean add(List<Object> e) {
            if (e == null) {
                throw new IllegalArgumentException("Adding null not allowed");
            }
            return super.add(e);
        }

        @Override
        public boolean addAll(Collection<? extends List<Object>> c) {
            for (Object sub : c) {
                if (sub == null) {
                    throw new IllegalArgumentException("Found a null in " + c);
                }
            }
            return super.addAll(c);
        }
    }
}
