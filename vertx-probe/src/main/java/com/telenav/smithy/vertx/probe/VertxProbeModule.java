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
package com.telenav.smithy.vertx.probe;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
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
            bind(key).toInstance(Probe.empty());
        } else {
            bind(key).toProvider(new ProbeProvider<>(probes, async))
                    .asEagerSingleton();
        }
        initialized = true;
    }

    private static final class ProbeProvider<O extends Enum<O>> implements Provider<Probe<O>> {

        private final List<Provider<? extends ProbeImplementation<? super O>>> all;
        private final boolean async;

        ProbeProvider(List<Provider<? extends ProbeImplementation<? super O>>> all,
                boolean async) {
            this.all = all;
            this.async = async;
        }

        @Override
        public Probe<O> get() {
            List<ProbeImplementation<? super O>> impls
                    = new ArrayList<>(all.size());
            for (Provider<? extends ProbeImplementation<? super O>> item : all) {
                impls.add(item.get());
            }
            Probe<O> result = Probe.create(impls);
            if (async) {
                return result.async();
            } else {
                return result;
            }
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
