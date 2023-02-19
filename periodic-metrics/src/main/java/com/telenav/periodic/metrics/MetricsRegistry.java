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

import java.time.Duration;
import java.util.Collection;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Singleton;

/**
 * Allows additional metrics to be registered into the metrics reporting system.
 * Simply implement and bind as an eager singleton.
 *
 * @author Tim Boudreau
 */
public abstract class MetricsRegistry {

    @SuppressWarnings("LeakingThisInConstructor")
    protected MetricsRegistry(MetricsRegistrar registrar) {
        registrar.register(this);
    }

    /**
     * Return any incremental metrics which will be written to the metrics sink
     * during operation and should be emitted and reset periodically.
     *
     * @return A collection of metrics
     */
    public Collection<? extends Metric> incrementalMetrics() {
        return emptySet();
    }

    /**
     * Return any on-demand computed metrics this registry is adding to the set
     * of periodic ones. Note this method may be called multiple times, in order
     * to create instances for different periods.
     *
     * @return A collection of on demand metrics
     */
    public Collection<? extends OnDemandMetric<Long>> onDemandMetrics() {
        return emptySet();
    }

    /**
     * Collect any statistic emitting metrics which use a single snapshot of
     * timings to emit multiple statistic values.
     *
     * @param samplingInterval The time period items are being requested for -
     * since these collect many samples, you will want to tune the number of
     * samples to use by the sampling period
     *
     * @return A collection of metrics
     */
    public Collection<? extends MultiMetric<Long>> multiMetrics(Duration samplingInterval) {
        return emptySet();
    }

    /**
     * Registrar for metrics which should be emitted periodically.
     */
    @Singleton
    public static class MetricsRegistrar {

        private final Set<MetricsRegistry> all = new HashSet<>();

        void register(MetricsRegistry reg) {
            all.add(reg);
        }

        Collection<? extends Metric> metrics() {
            Set<Metric> result = new TreeSet<>(Metric::compare);
            result.addAll(BuiltInMetrics.INCREMENTAL);
            for (MetricsRegistry reg : all) {
                result.addAll(reg.incrementalMetrics());
            }
            return unmodifiableSet(result);
        }

        Collection<? extends OnDemandMetric<Long>> onDemandMetrics() {
            Set<OnDemandMetric<Long>> result = new TreeSet<>();
            result.addAll(OnDemandMetric.defaultOnDemandMetrics());
            for (MetricsRegistry reg : all) {
                result.addAll(reg.onDemandMetrics());
            }
            return result;
        }

        Collection<? extends MultiMetric<Long>> multiMetrics(Duration period) {
            Set<MultiMetric<Long>> result = new HashSet<>();
            for (MetricsRegistry reg : all) {
                result.addAll(reg.multiMetrics(period));
            }
            return result;
        }

    }
}
