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
package com.telenav.periodic.metrics;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
public class MetricsModule implements Module {

    private Class<? extends OutboundMetricsSink> outbound;
    private final Set<Class<? extends MetricsRegistry>> registries = new HashSet<>();

    /**
     * Set the outbound message sink to use.
     *
     * @param c the sink type
     * @return this
     */
    public MetricsModule withOutboundMetricsSink(Class<? extends OutboundMetricsSink> c) {
        if (this.outbound != null && this.outbound != c) {
            throw new IllegalArgumentException("Cannot bind OutboundMetricsSink to " + c
                    + " - it is already bound to " + this.outbound);
        }
        this.outbound = c;
        return this;
    }

    /**
     * Add a metrics registry which should be initialized on startup.
     *
     * @param registryType The registry type
     * @return this
     */
    public MetricsModule withMetricsRegistry(Class<? extends MetricsRegistry> registryType) {
        registries.add(registryType);
        return this;
    }

    // A way to share a single instance of MetricsSinks between bindings for two
    // different types
    @Singleton
    private static final class MetricsSinkProvider implements Provider<MetricsSink> {

        private final MetricsSinks sinks;

        @Inject
        MetricsSinkProvider(MetricsSinks sinks) {
            this.sinks = sinks;
        }

        @Override
        public MetricsSink get() {
            return sinks;
        }
    }

    private static final class LastValuesLookupProvider implements
            Provider<Function<? super StandardMetricsPeriods, ? extends Map<Metric, Long>>> {

        private final MetricsSinks sinks;

        @Inject
        LastValuesLookupProvider(MetricsSinks sinks) {
            this.sinks = sinks;
        }

        @Override
        public Function<? super StandardMetricsPeriods, ? extends Map<Metric, Long>> get() {
            return sinks;
        }
    }

    @Override
    public void configure(Binder binder) {
        binder.bind(MetricsSink.class).toProvider(MetricsSinkProvider.class);
        if (outbound != null) {
            binder.bind(OutboundMetricsSink.class).to(outbound).in(Scopes.SINGLETON);
        }
        registries.forEach(reg -> binder.bind(reg).asEagerSingleton());
        binder.bind(new TypeLiteral<Function<? super StandardMetricsPeriods, ? extends Map<Metric, Long>>>() {
        }).toProvider(LastValuesLookupProvider.class);
        binder.bind(MetricsInit.class).asEagerSingleton();
    }

    static class MetricsInit {

        @Inject
        MetricsInit(MetricsSinks sinks) {
            sinks.checkInit();
        }
    }

}
