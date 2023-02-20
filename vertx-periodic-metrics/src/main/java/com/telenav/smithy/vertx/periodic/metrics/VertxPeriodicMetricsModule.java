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
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.telenav.periodic.metrics.MetricsModule;
import com.telenav.periodic.metrics.OutboundMetricsSink;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
final class VertxPeriodicMetricsModule<Op extends Enum<Op>> implements Module {

    public static final String GUICE_BINDING_OP_TYPE = "opType";
    private final Class<Op> opType;
    private final Class<? extends OutboundMetricsSink> sinkType;
    private final Class<? extends OperationWeights> weights;

    VertxPeriodicMetricsModule(Class<Op> opType, Class<? extends OutboundMetricsSink> sinkType,
            Class<? extends OperationWeights> weights) {
        this.opType = opType;
        this.sinkType = sinkType;
        this.weights = weights;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Binder binder) {
        binder.bind(new TypeLiteral<Class<?>>() {
        }).annotatedWith(Names.named(GUICE_BINDING_OP_TYPE)).toInstance(opType);
        binder.bind(SimpleOperationMetrics.class).asEagerSingleton();
        // The things we do for generic bindings
        Key<SimpleOperationMetrics<Op>> key
                = (Key<SimpleOperationMetrics<Op>>) Key.get(
                        new OneGenericFakeType<>(SimpleOperationMetrics.class, opType));
        binder.bind(key).toProvider(new TypedSimpleOperationMetrics<Op>(
                binder.getProvider(SimpleOperationMetrics.class)));

        Key<BiConsumer<Op, Duration>> key2
                = (Key<BiConsumer<Op, Duration>>) Key.get(new TwoGenericFakeType<>(BiConsumer.class, opType, Duration.class));
        binder.bind(key2).toInstance(new TimingMetricsConsumer<>(binder.getProvider(SimpleOperationMetrics.class), opType));

        binder.install(new MetricsModule()
                .withMetricsRegistry(SimpleOperationMetrics.class)
                .withOutboundMetricsSink(sinkType));
        if (weights != null) {
            binder.bind(OperationWeights.class).to(weights);
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

    private static final class OneGenericFakeType<R, T> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;

        public OneGenericFakeType(Class<R> topType, Class<T> genericType) {
            this.topType = topType;
            this.genericType = genericType;
        }

        public String getTypeName() {
            return topType.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType};
        }

        public Type getRawType() {
            return topType;
        }

        public Type getOwnerType() {
            return null;
        }

        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + '>';
        }
    }

    private static final class TwoGenericFakeType<R, T, U> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;
        private final Class<U> otherGenericType;

        public TwoGenericFakeType(Class<R> topType, Class<T> genericType, Class<U> otherGenericType) {
            this.topType = topType;
            this.genericType = genericType;
            this.otherGenericType = otherGenericType;
        }

        public String getTypeName() {
            return topType.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType, otherGenericType};
        }

        public Type getRawType() {
            return topType;
        }

        public Type getOwnerType() {
            return null;
        }

        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + ", " + otherGenericType.getSimpleName()
                    + '>';
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

}
