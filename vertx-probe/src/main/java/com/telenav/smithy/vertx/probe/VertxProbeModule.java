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
package com.telenav.smithy.vertx.probe;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Guice module which sets up a binding of Probe&lt;O&gt; for a given type, and
 * lets multiple probe implementations be plugged in.
 *
 * @author Tim Boudreau
 */
public final class VertxProbeModule<O extends Enum<O>> extends AbstractModule {

    private final List<Function<? super Binder, Provider<? extends ProbeImplementation<? super O>>>> all = new ArrayList<>();
    private final Class<O> type;
    private boolean async;
    private volatile boolean initialized;

    public VertxProbeModule(Class<O> type) {
        this.type = type;
    }

    /**
     * Make the probe implementation asynchronous - all probe calls will
     * <i>always</i>
     * be invoked (even during shutdown), but the work of doing that happens on
     * a background thread to minimize impact on service responsiveness.
     *
     * @return this
     */
    public VertxProbeModule<O> async() {
        async = true;
        return this;
    }

    /**
     * Provide a ProbeImplementation to be called on calls to methods on the
     * bound Probe.
     *
     * @param probe A probe implementation
     * @return this
     */
    public VertxProbeModule<O> withProbe(ProbeImplementation<? super O> probe) {
        checkInitialized();
        if (probe == null) {
            throw new IllegalArgumentException("Null probe");
        }
        all.add(_ignored -> Providers.of(probe));
        return this;
    }

    /**
     * Provide a ProbeImplementation type to be instantiated (possibly with
     * injection) and called on calls to methods on the bound probe.
     *
     * @param probeType
     * @return this
     */
    public VertxProbeModule<O> withProbe(Class<? extends ProbeImplementation<? super O>> probeType) {
        checkInitialized();
        if (probeType == null) {
            throw new IllegalArgumentException("Null probe type");
        }
        all.add(binder -> binder.getProvider(probeType));
        return this;
    }

    private void checkInitialized() {
        if (initialized) {
            throw new IllegalStateException("Cannot configure after injector initialization");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void configure() {
        List<Provider<? extends ProbeImplementation<? super O>>> probes = new ArrayList<>();
        for (Function<? super Binder, Provider<? extends ProbeImplementation<? super O>>> f : all) {
            probes.add(f.apply(binder()));
        }
        Key<Probe<O>> key
                = (Key<Probe<O>>) Key.get(new FakeProbeType<>(type));
        if (probes.isEmpty()) {
            Probe<O> empty = Probe.empty();
            bind(key).toInstance(empty);
            bind(new TypeLiteral<Probe<?>>() {
            }).toInstance(empty);
        } else {
            ProbeProvider<O> probeProvider = new ProbeProvider<>(probes, async);
            bind(key).toProvider(probeProvider)
                    .asEagerSingleton();
            bind(new TypeLiteral<Probe<?>>() {
            }).toProvider(probeProvider).in(Scopes.SINGLETON);
            // Don't bind this if we have no probes, or all exceptions go
            // into a black hold
            bind(Exceptions.class).asEagerSingleton();
        }

        initialized = true;
    }

    private static final class Exceptions implements Handler<Throwable> {

        private final Provider<Probe> probe;

        @Inject
        Exceptions(Vertx vx, Provider<Probe> probe) {
            // Set up the global vertx exception handler
            this.probe = probe;
            vx.exceptionHandler(this);
        }

        @Override
        public void handle(Throwable event) {
            String msg = event.getMessage();
            probe.get().onNonOperationFailure(msg == null ? "unknown" : msg, event);
        }
    }

    private static final class ProbeProvider<O extends Enum<O>> implements Provider<Probe<O>> {

        private final List<Provider<? extends ProbeImplementation<? super O>>> all;
        private final boolean async;
        private Probe<O> probe;
        private volatile boolean shutdown;

        ProbeProvider(List<Provider<? extends ProbeImplementation<? super O>>> all,
                boolean async) {
            this.all = all;
            this.async = async;
        }

        private void shutdown() {
            shutdown = true;
            Probe<O> pb;
            synchronized (this) {
                pb = probe;
            }
            if (pb != null) {
                pb.onShutdown();
                try {
                    pb.shutdown();
                } catch (InterruptedException ex) {
                    ex.printStackTrace(System.err);
                }
            }
        }

        @Override
        public synchronized Probe<O> get() {
            if (probe != null) {
                return probe;
            }
            List<ProbeImplementation<? super O>> impls
                    = new ArrayList<>(all.size());
            for (Provider<? extends ProbeImplementation<? super O>> item : all) {
                impls.add(item.get());
            }
            Probe<O> result = Probe.create(impls);
            if (async && !shutdown) {
                result = result.async();
                Thread t = new Thread(this::shutdown, "async-probe-shutdown");
                Runtime.getRuntime().addShutdownHook(t);
            }
            probe = result;
            result.onStartup();
            return result;
        }
    }

    /**
     * Needed to allow creating Guice Key instances against Probe with a type
     * parameter passed in as a Class object.
     *
     * @param <T> A type
     */
    private static class FakeProbeType<T>
            implements ParameterizedType {

        private final Class<T> typeParam;

        FakeProbeType(Class<T> typeParam) {
            this.typeParam = typeParam;
        }

        @Override
        public String toString() {
            return getTypeName();
        }

        @Override
        public String getTypeName() {
            return Probe.class.getName() + "<" + typeParam.getName() + ">";
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{typeParam};
        }

        @Override
        public Type getRawType() {
            return Probe.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}
