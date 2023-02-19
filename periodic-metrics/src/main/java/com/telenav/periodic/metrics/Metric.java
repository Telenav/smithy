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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * A metric - a key used to store collected metrics under, and provider of the
 * name used when logging metrics. Typically implemented over enums, the
 * contract requires that a metric
 * <ul>
 * <li>Have the same name for its lifetime</li>
 * <li>Implement equals() and hashCode() based on comparing the return value of
 * name() <i>alone</i></li>
 * <li>Return consistent values throughout its lifetime</li>
 * <li>Implement toString() to return loggingName()</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public interface Metric extends Serializable {

    /**
     * The programmatic name of this metric.
     *
     * @return A name
     */
    String name();

    /**
     * If true, don't log negative values - some metrics - particularly JMX
     * based memory metrics - will return -1 if the value cannot be retrieved at
     * all (for example, available disk space in $TMPDIR on Mac OS, or
     * allocatable off-heap memory - though that can be derived from command
     * line arguments if a value is passed).
     *
     * @return whether or not the value is meaningful when negative - returns
     * true by default
     */
    default boolean omitIfNegative() {
        return true;
    }

    /**
     * If true, do not log zero values - this can be either because zero
     * indicates no valid value is available, or simply because the value is
     * usually zero and is just log-spam when zero. The default implementation
     * returns false.
     *
     * @return Whether or not to include the value when it is zero
     */
    default boolean omitIfZero() {
        return false;
    }

    /**
     * The name to use when logging - these are typically implemented over enums
     * with all-caps names; the default behavior is to convert name() to
     * lower-case and replace _'s with -'s.
     *
     * @return
     */
    default String loggingName() {
        return name().toLowerCase().replace('_', '-');
    }

    default boolean isOnDemand() {
        return false;
    }

    /**
     * Metric cannot extend Comparable&lt;Metric&gt; or it will conflict with
     * the inherent implementation of Comparable on enums, so this method serves
     * as a canonical comparator for metrics.
     *
     * @param a A metric
     * @param b Another metric
     * @return a comparison
     */
    public static int compare(Metric a, Metric b) {
        return a.name().compareToIgnoreCase(b.name());
    }

    /**
     * Creates a new map of metric to LongAdder for only those metrics in the
     * passed collection that report that they are <i>not on-demand</i>; used to
     * create a new value for timed metric aggregators.
     *
     * @param c A collection of metrics
     * @return A map of metric to LongAdder
     */
    public static Map<Metric, LongAdder> newMap(Collection<? extends Metric> c) {
        Map<Metric, LongAdder> result = new HashMap<>(); //new TreeMap<>(Metric::compare);
        for (Metric m : c) {
            if (!m.isOnDemand()) {
                result.put(m, new LongAdder());
            }
        }
        return result;
    }

    /**
     * Create an operation-specific metric which combines an operation and a
     * standard metric such as MetricKind.HTTP_REQUESTS to count events specific
     * to an operation.
     *
     * @param <Op> The operation enum type
     * @param <M> The metric enum type
     * @param operation An operation
     * @param metric A metric
     * @return A metric
     */
    public static <Op extends Enum<Op>, M extends Enum<M> & Metric> OperationMetric<Op, M>
            operationMetric(Op operation, M metric) {
        return new OperationMetric<>(operation, metric);
    }
}
