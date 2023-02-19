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

import com.mastfrog.function.TriConsumer;
import com.mastfrog.settings.Settings;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.telenav.periodic.metrics.MetricsRegistry;
import com.telenav.periodic.metrics.MultiMetric;
import com.telenav.periodic.metrics.OperationStatsMetric;
import com.telenav.periodic.metrics.PercentileMethod;
import com.telenav.periodic.metrics.SampleProbability;
import com.telenav.smithy.vertx.probe.Probe;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.emptyList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Provider;

/**
 * Base class for registering per-operation metrics.
 *
 * @author Tim Boudreau
 * @param <Op> The operation type for the service
 */
public abstract class AbstractOperationMetrics<Op extends Enum<Op>> extends MetricsRegistry {

    protected static final int DEFAULT_HARD_STATS_BUCKET_LIMIT = 5050000;
    protected static final int DEFAULT_REQ_PER_SECOND = 84000;
    public static final String SETTINGS_KEY_REQUESTS_PER_SECOND = "req.per.second.target";
    public static final String SETTINGS_KEY_MAX_STATS_BUCKETS = "max.stats.buckets";
    protected static final int MIN_BUCKETS = 32;
    protected final Map<Op, List<OperationStatsMetric<Op>>> operationSinks;
    protected final List<OperationStatsMetric<All>> overall = new ArrayList<>();
    protected final int targetRequestsPerSecond;
    protected final int maxStatsBuckets;
    protected final Provider<Probe<Op>> probe;
    protected final Class<Op> opType;

    protected AbstractOperationMetrics(MetricsRegistrar registrar, Class<Op> opType, Settings settings,
            Provider<Probe<Op>> probe) {
        super(registrar);
        this.opType = opType;
        operationSinks = new EnumMap<>(opType);
        targetRequestsPerSecond = settings.getInt(SETTINGS_KEY_REQUESTS_PER_SECOND, DEFAULT_REQ_PER_SECOND);
        maxStatsBuckets = max(MIN_BUCKETS, settings.getInt(SETTINGS_KEY_MAX_STATS_BUCKETS, DEFAULT_HARD_STATS_BUCKET_LIMIT));
        this.probe = probe;
    }

    public final void addTime(Op op, Duration dur) {
        addTime(op, dur.toMillis());
    }

    public final void addTime(Op op, long millis) {
        List<OperationStatsMetric<Op>> targets = operationSinks.get(op);
        if (targets != null) {
            assert !targets.isEmpty() : "No targets for " + op;
            for (OperationStatsMetric<Op> t : targets) {
                t.add(millis);
            }
        } else {
            throw new IllegalArgumentException("No sinks for " + op);
        }
        for (OperationStatsMetric<All> agg : overall) {
            agg.add(millis);
        }
    }

    @Override
    public final Collection<? extends MultiMetric<Long>> multiMetrics(Duration samplingInterval) {
        if (Boolean.getBoolean("unit.test")) {
            return emptyList();
        }
        List<OperationStatsMetric<?>> result = new ArrayList<>();
        // We use Probe here for logging, so we don't force the subclasser into
        // depending on a particular logging framework
        Map<String, Object> logRecord = new LinkedHashMap<>();
        logRecord.put("samplingInterval", samplingInterval);
        logRecord.put("targetRequestsPerSecond", targetRequestsPerSecond);
        logRecord.put("maxStatsBuckets", maxStatsBuckets);
        logRecord.put("minBuckets", MIN_BUCKETS);
        logRecord.put("opType", opType);
        for (Op op : opType.getEnumConstants()) {
            withPercentileAndSampleCount(op, samplingInterval, (buckets, probability, method) -> {
                int weightedBuckets = (int) max(MIN_BUCKETS, round(buckets * operationWeight(op)));
                OperationStatsMetric<Op> opMetric = new OperationStatsMetric<>(op, weightedBuckets, probability, method);
                operationSinks.computeIfAbsent(op, o -> new ArrayList<>()).add(opMetric);
                result.add(opMetric);
                logRecord.put(op.name().toLowerCase().replace('_', '-'), map("buckets").to(weightedBuckets)
                        .map("sampleRate").to(probabilityToString(probability))
                        .map("percentileMethod").finallyTo(method.name()));
            });
        }
        withPercentileAndSampleCount(null, samplingInterval, (buckets, probability, method) -> {
            OperationStatsMetric<All> newAll = new OperationStatsMetric<>(All.ALL, buckets, probability, method);
            logRecord.put("all", map("buckets").to(buckets)
                    .map("sampleRate").to(probabilityToString(probability))
                    .map("percentileMethod").finallyTo(method.name()));
            overall.add(newAll);
            result.add(newAll);
        });
        probe.get().onEvent("initMetrics", logRecord);
        return result;
    }

    private String probabilityToString(SampleProbability probability) {
        return probability == null ? "100%" : probability.toString();
    }

    private void withPercentileAndSampleCount(Op op, Duration dur, TriConsumer<Integer, SampleProbability, PercentileMethod> c) {
        long seconds = dur.toSeconds();
        int targetSamples = (int) min(Integer.MAX_VALUE, targetRequestsPerSecond * seconds);
        PercentileMethod method;
        SampleProbability probability;
        int buckets;
        if (targetSamples > maxStatsBuckets) {
            buckets = maxStatsBuckets;
            method = PercentileMethod.INTERPOLATED;
            double val = (double) maxStatsBuckets / (double) (targetRequestsPerSecond * seconds);
            probability = SampleProbability.nearest(val);
        } else {
            buckets = targetSamples;
            method = PercentileMethod.NEAREST;
            probability = null;
        }
        int weightedBuckets = (int) max(MIN_BUCKETS, round(buckets * operationWeight(op)));
        c.accept(weightedBuckets, probability, method);
    }

    protected abstract double operationWeight(Op op);

    /**
     * A placeholder enum for aggregate metrics over *all* operations.
     */
    public enum All {
        ALL
    }
}
