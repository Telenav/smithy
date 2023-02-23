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
package com.telenav.smithy.vertx.periodic.metrics;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.telenav.periodic.metrics.MetricsModule;
import com.telenav.periodic.metrics.OutboundMetricsSink;
import static com.telenav.smithy.vertx.periodic.metrics.VertxMetricsSupport.GUICE_BINDING_OP_TYPE;
import com.telenav.vertx.guice.util.GuiceUtils;
import java.time.Duration;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
final class VertxPeriodicMetricsModule<Op extends Enum<Op>> implements Module {

    private final Class<Op> opType;
    private final Class<? extends OutboundMetricsSink> sinkType;
    private final Class<? extends OperationWeights> weights;
    private final boolean collectDbTimings;

    VertxPeriodicMetricsModule(Class<Op> opType, Class<? extends OutboundMetricsSink> sinkType,
            Class<? extends OperationWeights> weights, boolean collectDbTimings) {
        this.opType = opType;
        this.sinkType = sinkType;
        this.weights = weights;
        this.collectDbTimings = collectDbTimings;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Binder binder) {
        // Bind the operation class so that generic implementations like LoggingProbe
        // can find it
        binder.bind(new TypeLiteral<Class<?>>() {
        }).annotatedWith(Names.named(GUICE_BINDING_OP_TYPE)).toInstance(opType);

        // Bind the raw SimpleOperationMetrics as an eager singleton, so its set of
        // metrics are pulled in the first time something requests injection of an
        // instance of MetricsRegistrar
        binder.bind(SimpleOperationMetrics.class).asEagerSingleton();

        // The things we do for generic bindings...
        //
        // This ensures that anything that wants one in application code is able to
        // request injection of SimpleOperationMetrics<Op>, rather than the untyped
        // version
        Key<SimpleOperationMetrics<Op>> key
                = GuiceUtils.keyForGenericType(SimpleOperationMetrics.class, opType);

        binder.bind(key).toProvider(new TypedSimpleOperationMetrics<Op>(
                binder.getProvider(SimpleOperationMetrics.class)));

        Key<BiConsumer<Op, Duration>> key2
                = GuiceUtils.keyForGenericType(BiConsumer.class, opType, Duration.class);

        binder.bind(key2).toInstance(new TimingMetricsConsumer<>(binder.getProvider(SimpleOperationMetrics.class), opType));

        // So that untyped implementations like the general-purpose LoggingProbe can work,
        // also bind as untyped
        binder.bind(new TypeLiteral<BiConsumer<Enum<?>, Duration>>() {
        }).toProvider(UntypedMetricsConsumer.class).in(Scopes.SINGLETON);

        binder.install(new MetricsModule()
                .withMetricsRegistry(SimpleOperationMetrics.class)
                .withOutboundMetricsSink(sinkType));
        if (weights != null) {
            binder.bind(OperationWeights.class).to(weights);
        }

        if (collectDbTimings) {
            binder.bind(DbTimingConsumer.class).asEagerSingleton();
            binder.bind(ClientTimingConsumer.class).toProvider(ClientTimingsOverDbTimingConsumerProvider.class);
        }
    }

    static class ClientTimingsOverDbTimingConsumerProvider implements Provider<ClientTimingConsumer> {

        private final DbTimingConsumer timingConsumer;

        @Inject
        ClientTimingsOverDbTimingConsumerProvider(DbTimingConsumer timingConsumer) {
            this.timingConsumer = timingConsumer;
        }

        @Override
        public ClientTimingConsumer get() {
            return timingConsumer;
        }
    }

    static class TypedSimpleOperationMetrics<Op extends Enum<Op>> implements Provider<SimpleOperationMetrics<Op>> {

        @SuppressWarnings("rawType")
        private final Provider<SimpleOperationMetrics> rawProvider;

        @SuppressWarnings("rawType")
        TypedSimpleOperationMetrics(Provider<SimpleOperationMetrics> rawProvider) {
            this.rawProvider = rawProvider;
        }

        @Override
        @SuppressWarnings("unchecked")
        public SimpleOperationMetrics<Op> get() {
            return rawProvider.get();
        }
    }

    private static final class TimingMetricsConsumer<Op extends Enum<Op>> implements BiConsumer<Op, Duration> {

        private final Provider<SimpleOperationMetrics> mx;
        private final Class<Op> opType;

        @Inject
        TimingMetricsConsumer(Provider<SimpleOperationMetrics> mx, Class<Op> opType) {
            this.mx = mx;
            this.opType = opType;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void accept(Op op, Duration u) {
            assert opType.isInstance(op);
            mx.get().addTime(op, u);
        }
    }

    static class UntypedMetricsConsumer implements Provider<BiConsumer<Enum<?>, Duration>> {

        @SuppressWarnings("rawType")
        private final Provider<SimpleOperationMetrics> rawProvider;
        private BiConsumer<Enum<?>, Duration> consumer;

        @SuppressWarnings("rawType")
        @Inject
        UntypedMetricsConsumer(Provider<SimpleOperationMetrics> rawProvider) {
            this.rawProvider = rawProvider;
        }

        @Override
        @SuppressWarnings("unchecked")
        public BiConsumer<Enum<?>, Duration> get() {
            return consumer == null ? consumer = rawProvider.get()::addTime : consumer;
        }
    }

}
