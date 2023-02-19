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

import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.telenav.periodic.metrics.MetricsRegistry.MetricsRegistrar;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of MetricsSink which aggregates metrics and logs them
 * periodically.
 *
 * @author Tim Boudreau
 */
@Singleton
public class MetricsSinks implements MetricsSink, Function<StandardMetricsPeriods, Map<Metric, Long>> {

    public static final String SETTINGS_KEY_OMIT_GC_METRICS = "omit.gc.metrics";

    private final MetaMetricsSink all = new MetaMetricsSink();
    private final ShutdownHookRegistry hooks;
    private final OutboundMetricsSink out;
    private final List<OnDemandMetric<Long>> onDemand = new ArrayList<>();
    private final Map<StandardMetricsPeriods, AtomicReference<Map<Metric, Long>>> lastValues = newLastValuesMap();
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final MetricsRegistrar registrar;
    private final boolean omitGcMetrics;
    private ClockInterval.PeriodicQueue<SimpleMetricsSink> q;

    @Inject
    MetricsSinks(ShutdownHookRegistry hooks, OutboundMetricsSink out, Settings settings, MetricsRegistrar registrar) {
        this.hooks = hooks;
        this.out = out;
        this.registrar = registrar;
        omitGcMetrics = settings.getBoolean(SETTINGS_KEY_OMIT_GC_METRICS, false);
    }

    private static Map<StandardMetricsPeriods, AtomicReference<Map<Metric, Long>>> newLastValuesMap() {
        Map<StandardMetricsPeriods, AtomicReference<Map<Metric, Long>>> result
                = new EnumMap<>(StandardMetricsPeriods.class);
        for (StandardMetricsPeriods p : StandardMetricsPeriods.values()) {
            result.put(p, new AtomicReference<>(emptyMap()));
        }
        return result;
    }

    public void checkInit() {
        if (initialized.compareAndSet(false, true)) {
            init();
        }
    }

    private void init() {
        Map<StandardMetricsPeriods, ClockInterval<SimpleMetricsSink>> intervals
                = new EnumMap<>(StandardMetricsPeriods.class);
        for (StandardMetricsPeriods p : StandardMetricsPeriods.values()) {
            ClockInterval<SimpleMetricsSink> mx = p.create(registrar, omitGcMetrics);
            all.add(mx.get());
            intervals.put(p, mx);
        }
        q = new ClockInterval.PeriodicQueue<>(hooks, intervals.values(), this::onExpiry);
        for (OnDemandMetric<Long> m : registrar.onDemandMetrics()) {
            if (m.isValid()) {
                onDemand.add(m);
            }
        }
        if (!(out instanceof NoOpOutboundMessageSink)) {
            q.start();
        }
    }

    static class MetricsMapFactory implements Supplier<Map<Metric, LongAdder>> {

        private final Collection<? extends Metric> metrix;

        public MetricsMapFactory(Collection<? extends Metric> metrix) {
            this.metrix = metrix;
        }

        @Override
        public Map<Metric, LongAdder> get() {
            return Metric.newMap(metrix);
        }
    }

    @Override
    public Map<Metric, Long> apply(StandardMetricsPeriods t) {
        return unmodifiableMap(lastValues.get(t).get());
    }

    private void onExpiry(ClockInterval<SimpleMetricsSink> iv, boolean wasStarted) {
        SimpleMetricsSink sink = iv.get();
        Map<Metric, LongAdder> collectedMetrics = sink.reset();
        Map<Metric, Long> values = new TreeMap<>(Metric::compare);
        StandardMetricsPeriods period = StandardMetricsPeriods.valueFor(iv.spec());
        // Use no-op log for unstarted logs so we do reset cumulations, but
        // we do not actually emit any log record for unstarted logs (otherwise
        // we would emit an empty log record with just the name, which is confusing
        out.batch(period.toDuration(), sink.name() + "-metrics", wasStarted, bcc -> {
            collectedMetrics.forEach((kind, action) -> {
                long val = action.sum();
                if (val == 0L && kind.omitIfZero()) {
                    return;
                }
                values.put(kind, val);
                bcc.accept(kind, val);
            });
            for (OnDemandMetric<Long> odm : onDemand) {
                if (odm.isValid()) {
                    long val = odm.get();
                    Metric kind = odm.kind();
                    if (val == 0L && kind.omitIfZero()) {
                        continue;
                    }
                    bcc.accept(odm.kind(), val);
                    values.put(odm.kind(), val);
                }
            }
            sink.additionalMetrics((m, val) -> {
                if ((val == 0L && m.omitIfZero()) || val < 0 && m.omitIfNegative()) {
                    return;
                }
                bcc.accept(m, val);
                values.put(m, val);
            });
        });
        lastValues.get(period).set(values);
    }

    @Override
    public void onMetric(Metric kind, long amount) {
//        checkInit();
        if (out instanceof NoOpOutboundMessageSink) {
            return;
        }
        all.onMetric(kind, amount);
    }

    @Override
    public String toString() {
        return "MetricsSinks(" + all + ")";
    }
}
