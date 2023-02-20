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
import com.mastfrog.util.preconditions.ConfigurationError;
import com.telenav.periodic.metrics.MetricsRegistry;
import com.telenav.periodic.metrics.MultiMetric;
import com.telenav.periodic.metrics.OperationStatsMetric;
import com.telenav.periodic.metrics.PercentileMethod;
import com.telenav.periodic.metrics.SampleProbability;
import static com.telenav.smithy.vertx.periodic.metrics.VertxMetricsSupport.SETTINGS_KEY_MAX_STATS_BUCKETS;
import static com.telenav.smithy.vertx.periodic.metrics.VertxMetricsSupport.SETTINGS_KEY_REQUESTS_PER_SECOND;
import static com.telenav.smithy.vertx.periodic.metrics.VertxPeriodicMetricsModule.GUICE_BINDING_OP_TYPE;
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
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

/**
 * Base class for registering per-operation metrics.
 *
 * @author Tim Boudreau
 * @param <Op> The operation type for the service
 */
final class SimpleOperationMetrics<Op extends Enum<Op>> extends MetricsRegistry {

    protected static final int DEFAULT_HARD_STATS_BUCKET_LIMIT = 5050000;
    protected static final int DEFAULT_REQ_PER_SECOND = 84000;
    protected static final int MIN_BUCKETS = 32;
    protected final Map<Op, List<OperationStatsMetric<Op>>> operationSinks;
    protected final List<OperationStatsMetric<All>> overall = new ArrayList<>();
    protected final Provider<Probe<Op>> probe;
    protected final Class<Op> opType;
    private final Provider<Settings> settings;
    private final Provider<OperationWeights> weights;

    @Inject
    @SuppressWarnings("unchecked")
    SimpleOperationMetrics(@Named(GUICE_BINDING_OP_TYPE) Class<?> opType, MetricsRegistrar registrar, Provider<Settings> settings,
            Provider<Probe<?>> probe, Provider<OperationWeights> weights) {
        super(registrar);
        this.settings = settings;
        if (!opType.isEnum()) {
            throw new ConfigurationError("Not a enum type: " + opType);
        }
        // Guice cannot handle incompletely specified types, so we have to use ? and cast
        this.opType = (Class<Op>) opType;
        this.probe = (Provider<Probe<Op>>) (Provider) probe;
        this.weights = weights;
        operationSinks = new EnumMap<>(this.opType);
    }

    private int targetRequestsPerSecond() {
        return settings.get().getInt(SETTINGS_KEY_REQUESTS_PER_SECOND, DEFAULT_REQ_PER_SECOND);
    }

    private int maxStatsBuckets() {
        return max(MIN_BUCKETS, settings.get().getInt(SETTINGS_KEY_MAX_STATS_BUCKETS, DEFAULT_HARD_STATS_BUCKET_LIMIT));
    }

    /**
     * Determine the multiplier to use when computing the number of metrics
     * buckets (an AtomicIntegerArray) to allocate for each operation in order
     * to collect timing metrics. The timings are typically request duration
     * from initiation to flushing the last byte of the response. These are
     * stored as unsigned ints (so requests longer than 14 hours in duration
     * will be ignored).
     * <p>
     * The number of buckets allocated is
     * <code>targetRequestsPerSecond * samplingInterval.toSeconds() * operationWeight(op)</code>
     * so, if you expect (or are configuring a load balancer to limit you to)
     * 1000 requests per second, then you need 600,000 stats buckets to collect
     * one minute's worth of data for all operations at maximum load (and this
     * will be really be the number used for the <i>all</i>
     * bucket which aggregates across all HTTP). But if the operation is only
     * likely to be 1/3 of your operations, then the number of buckets actually
     * alloacted can be reduced to <i>1000 req * 60 seconds * 0.33 = 198,000</i>
     * buckets.
     * </p>
     * <p>
     * Buckets are allocated once on startup and reused, in pairs of sets of
     * buckets, plus an array used for statistical calculations against a
     * snapshot, so the actual memory requirements for one mintue's worth of
     * complete metrics at 1000 requests per second is <i>198,000 buckets * 8
     * bytes = 4,752,000 bytes</i> (subject to reduction via pending
     * optimization).
     * </p>
     * <p>
     * If more requests occur in a period than there are buckets, then writes
     * will wrap around and early requests for the target period are lost.
     * </p>
     * <p>
     * If the required number of buckets exceeds the hard maximum number of
     * stats buckets, then stats capturing, then a only random sample of timings
     * will be collected, such that the percentage of requests sampled fits
     * within the available buckets, but this means that statistics are
     * approximate.
     * </p>
     * <p>
     * The default implementation simply returns <code>1D</code> for null, and
     * <code>1D / Op.getEnumConstants().length</code> for an even distributions
     * of buckets across operations (which will never be the case in real life).
     * </p>
     * <p>
     * A hard maximum number of stats buckets can be set with the setting
     * <code>max.stats.buckets</code>, and will not be exceeded; the expected
     * overall requests per second can be set with the setting
     * <code>req.per.second.target</code>. Bear in mind that vertx-based servers
     * are capable of handling hundreds of thousands of requests per second, so
     * practical values for these should be large unless there is a known cap.
     * The default is an expectation of handling <i>84,000</i> requests per
     * second.
     * </p>
     *
     * @param op The operation, or null for all operations
     * @return A positive double greater than zero and less or equal to than
     * one.
     */
    protected double operationWeight(Op op) {
        return weights.get().operationWeight(op);
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
        logRecord.put("targetRequestsPerSecond", targetRequestsPerSecond());
        logRecord.put("maxStatsBuckets", maxStatsBuckets());
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
        int targetRequestsPerSecond = targetRequestsPerSecond();
        int maxStatsBuckets = maxStatsBuckets();
        int targetSamples = (int) min(Integer.MAX_VALUE, targetRequestsPerSecond() * seconds);
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

    /**
     * A placeholder enum for aggregate metrics over *all* operations.
     */
    public enum All {
        ALL
    }
}
