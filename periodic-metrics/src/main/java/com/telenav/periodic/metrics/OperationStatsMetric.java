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

import com.mastfrog.concurrent.FlipFlop;
import com.mastfrog.concurrent.stats.LongStatisticCollector;
import static com.mastfrog.util.collections.CollectionUtils.immutableSetOf;
import com.mastfrog.util.collections.LongList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;

/**
 * MultiMetric which collects request timings and emits computed statistics from
 * all of the timings taken for a period. Emits standard statistical metrics
 * such as p10, median, p90, p99, min, max, mean, count.
 *
 * @author Tim Boudreau
 */
public final class OperationStatsMetric<Op extends Enum<Op>> implements MultiMetric<Long> {

    public static final int DEFAULT_SAMPLES = 2048;
    private final Op operation;
    private final FlipFlop<LongStatisticCollector> collector;
    private final int samples;
    private final Metric median;
    private final Metric min;
    private final Metric max;
    private final Metric mean;
    private final Metric p90;
    private final Metric p10;
    private final Metric p99;
    private final Metric count;
    private final PercentileMethod percentileMethod;
    private final LongList list;

    /**
     * Create a metric using DEFAULT_SAMPLES samples.
     *
     * @param operation The operation
     */
    public OperationStatsMetric(Op operation) {
        this(operation, DEFAULT_SAMPLES, PercentileMethod.INTERPOLATED);
    }

    public OperationStatsMetric(Op operation, int samples, PercentileMethod percentileCalculation) {
        this(operation, samples, null, percentileCalculation);
    }

    /**
     * Create an OperationStatsMetric for a given operation.
     *
     * @param operation The operation
     * @param samples The number of samples to keep between polls
     * @param probability - the probability of taking a sample, given input -
     * this can be used to get a representative collection of samples (rather
     * than the last few) for extremely high traffic operations where keeping a
     * couple of arrays around large enough to capture the timing of every
     * request in an hour is impractical.
     * @param percentileCalculation The algorithm to use to compute percentile
     * values
     */
    public OperationStatsMetric(Op operation, int samples, @Nullable SampleProbability probability,
            PercentileMethod percentileCalculation) {
        this.operation = operation;
        this.samples = samples;
        this.percentileMethod = percentileCalculation == null ? PercentileMethod.INTERPOLATED : percentileCalculation;
        collector = statisticCollectorFlipFlop(samples, probability);
        list = LongList.create(samples);
        median = Metric.operationMetric(operation, StatisticalMetrics.MEDIAN);
        min = Metric.operationMetric(operation, StatisticalMetrics.MIN);
        max = Metric.operationMetric(operation, StatisticalMetrics.MAX);
        mean = Metric.operationMetric(operation, StatisticalMetrics.MEAN);
        p90 = Metric.operationMetric(operation, StatisticalMetrics.P90);
        p10 = Metric.operationMetric(operation, StatisticalMetrics.P10);
        p99 = Metric.operationMetric(operation, StatisticalMetrics.P99);
        count = Metric.operationMetric(operation, BuiltInMetrics.HTTP_REQUESTS);
    }

    public Op operation() {
        return operation;
    }

    public static <O extends Enum<O>> Set<OperationStatsMetric<O>> forEnum(Class<O> opEnumType, int samples, PercentileMethod percentileCalculation) {
        return forEnum(opEnumType, samples, null, percentileCalculation);
    }

    public static <O extends Enum<O>> Set<OperationStatsMetric<O>> forEnum(Class<O> opEnumType, int samples,
            @Nullable SampleProbability randomlySample, PercentileMethod percentileCalculation) {
        Set<OperationStatsMetric<O>> result = new HashSet<>();
        for (O o : opEnumType.getEnumConstants()) {
            result.add(new OperationStatsMetric<>(o, samples, randomlySample, percentileCalculation));
        }
        return result;
    }

    @Override
    public void add(long millis) {
        collector.get().accept(millis);
    }

    @Override
    public boolean get(BiConsumer<Metric, Long> c) {
        LongStatisticCollector stats = collector.flip();
        LongList values = list;
        list.clear();
        boolean result = result = stats.withStatsAndValues(values::add, (minimum, maximum, sum, count) -> {
            c.accept(min, minimum);
            c.accept(max, maximum);
            c.accept(mean, sum / count);
        });
        c.accept(this.count, (long) values.size());
        if (!values.isEmpty()) {
            values.sort();
            switch (values.size()) {
                case 0:
                    c.accept(this.count, 0L);
                    break;
                case 1:
                    long only = values.getAsLong(0);
                    c.accept(this.p10, only);
                    c.accept(this.median, only);
                    c.accept(this.p90, only);
                    c.accept(this.p99, only);
                    break;
                case 2:
                    long first = values.getAsLong(0);
                    long sec = values.getAsLong(1);
                    c.accept(this.p10, first);
                    c.accept(this.median, (first + sec / 2));
                    c.accept(this.p90, sec);
                    c.accept(this.p99, sec);
                    break;
                case 3:
                    long second = values.getAsLong(1);
                    c.accept(this.p10, values.getAsLong(0));
                    c.accept(this.median, second);
                    c.accept(this.p90, values.getAsLong(2));
                    c.accept(this.p99, values.getAsLong(2));
                    break;
                case 4:
                    long p90avg = (values.last() + values.getAsLong(values.size() - 2)) / 2;
                    long midavg = (values.get(1) + values.get(2)) / 2;
                    c.accept(this.p10, values.get(0));
                    c.accept(this.median, midavg);
                    c.accept(this.p90, p90avg);
                    c.accept(this.p99, values.last());
                    break;
                case 5:
                    long p90avg5 = (values.last() + values.getAsLong(values.size() - 2)) / 2;
                    c.accept(this.p10, values.get(0));
                    c.accept(this.median, values.get(2));
                    c.accept(this.p90, p90avg5);
                    c.accept(this.p99, values.get(4));
                    break;
                case 10:
                    c.accept(this.p10, values.get(0));
                    c.accept(this.median, values.get(4));
                    c.accept(this.p90, values.get(8));
                    c.accept(this.p99, values.get(9));
                    break;
                case 100:
                    c.accept(this.p10, values.get(9));
                    c.accept(this.median, values.get(49));
                    c.accept(this.p90, values.get(89));
                    c.accept(this.p99, values.get(99));
                    break;
                case 200:
                    c.accept(this.p10, values.get(9 * 2));
                    c.accept(this.median, values.get(49 * 2));
                    c.accept(this.p90, values.get(89 * 2));
                    c.accept(this.p99, values.get(99 * 2));
                    break;
                case 1000:
                    c.accept(this.p10, values.get(99));
                    c.accept(this.median, values.get(499));
                    c.accept(this.p90, values.get(890));
                    c.accept(this.p99, values.get(990));
                    break;
                default:
                    c.accept(this.p10, percentileMethod.value(0.1, values));
                    c.accept(this.median, percentileMethod.value(0.5, values));
                    c.accept(this.p90, percentileMethod.value(0.9, values));
                    c.accept(this.p99, percentileMethod.value(0.99, values));
                    break;
            }
        }
        return result;
    }

    public Collection<? extends Metric> metrics() {
        return immutableSetOf(median, min, max, mean, p90, count, p10, p99);
    }

    /**
     * We may be allocating very large AtomicLongArrays if we want not to ever
     * run out of stats slots between lengthy periods (like, 90K requests * 8
     * byte longs = 720k). So, rather than add nondeterminism by reallocating,
     * stats collectors can simply flip between two LongStatsCollector instances
     * that are allocated once and live the lifetime of the JVM. The reset
     * operation on LongStatisticCollector simply sets an AtomicInteger to 0, so
     * it is very low cost.
     * <p>
     * This gives us a pair of identically configured LongStatisticCollectors;
     * when emitting, the caller calls <code>flip()</code> to retrieve the
     * previously in use statistic collection, while other threads proceed with
     * the other instance which will now be returned by get().
     * </p>
     * <p>
     * There is a slight risk of race in the case that it takes more than one
     * minute to emit all of the metrics and the period is one minute (the
     * shortest period we support). In practice, this would take extraordinary
     * circumstances to encounter.
     * </p>
     */
    private static FlipFlop<LongStatisticCollector> statisticCollectorFlipFlop(int size,
            @Nullable SampleProbability probability) {
        LongStatisticCollector aa = LongStatisticCollector.ofUnsignedInts(size);
        LongStatisticCollector bb = LongStatisticCollector.ofUnsignedInts(size);
        if (probability != null) {
            BooleanSupplier supp = probability.supplier(size);
            aa = aa.intermittentlySampling(supp);
            bb = bb.intermittentlySampling(supp);
        }
        return new FlipFlop<>(aa, bb, LongStatisticCollector::reset);
    }

    @Override
    public String toString() {
        return "OpStats(" + operation + " " + samples + ")";
    }
}
