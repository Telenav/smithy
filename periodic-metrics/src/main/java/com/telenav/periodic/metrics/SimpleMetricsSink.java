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

import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * A simple map based metrics sink.
 *
 * @author Tim Boudreau
 */
final class SimpleMetricsSink implements MetricsSink {

    private final AtomicReference<Map<Metric, LongAdder>> metrics;
    private final List<OnDemandMetric<Long>> onDemandMetrics = new ArrayList<>(12);
    private final String name;
    private final Supplier<Map<Metric, LongAdder>> mapFactory;
    private final List<MultiMetric<Long>> multiMetrics = new ArrayList<>(32);

    SimpleMetricsSink(String name, Supplier<Map<Metric, LongAdder>> mapFactory) {
        this.name = name;
        this.mapFactory = mapFactory;
        metrics = new AtomicReference<>(mapFactory.get());
    }

    SimpleMetricsSink addMultiMetrics(Collection<? extends MultiMetric<Long>> c) {
        multiMetrics.addAll(c);
        return this;
    }

    SimpleMetricsSink includeGcMetrics() {
        onDemandMetrics.addAll(GCMetrics.defaultGcMetrics());
        return this;
    }

    public String name() {
        return name;
    }

    @Override
    public void onMetric(Metric kind, long amount) {
        LongAdder adder = metrics.get().get(kind);
        if (adder == null) {
            return;
        }
        metrics.get().get(kind).add(amount);
    }

    public Map<Metric, LongAdder> reset() {
        return metrics.getAndSet(mapFactory.get());
    }

    public void additionalMetrics(BiConsumer<Metric, Long> c) {
        for (OnDemandMetric<Long> odm : onDemandMetrics) {
            long val = odm.get();
            Metric k = odm.kind();
            if (val == 0L && k.omitIfZero()) {
                continue;
            }
            c.accept(k, val);
        }
        for (MultiMetric<Long> m : multiMetrics) {
            m.get(c);
        }
    }

    @Override
    public String toString() {
        return name + " with [" + Strings.join(',', metrics.get().keySet()) + "]";
    }
}
