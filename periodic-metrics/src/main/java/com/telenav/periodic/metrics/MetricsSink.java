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

import com.google.inject.ImplementedBy;
import static java.util.Arrays.asList;
import java.util.Collection;

/**
 * A place metrics are emitted into as they are collected, which may aggregate,
 * or log, or do something else with them.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(value = NoOpMetricsSink.class)
public interface MetricsSink {

    /**
     * A do-nothing metrics sink - useful to bind in tests where metrics
     * collection is not desired.
     */
    static MetricsSink NO_OP = new NoOpMetricsSink();

    /**
     * Add a new metric value into this sink.
     *
     * @param kind The metric in question
     * @param amount The value to append
     */
    void onMetric(Metric kind, long amount);

    /**
     * Calls onMetric with a value of 1L.
     *
     * @param kind The metric kind
     */
    default void onIncrement(Metric kind) {
        onMetric(kind, 1L);
    }

    /**
     * Wrap a bunch of metrics sinks in a single one that emits into all of
     * them.
     *
     * @param sinks An array of sinks
     * @return A MetricsSink
     */
    public static MetricsSink aggregateMetricsSinks(MetricsSink... sinks) {
        return new MetaMetricsSink(asList(sinks));
    }

    /**
     * Wrap a bunch of metrics sinks in a single one that emits into all of
     * them.
     *
     * @param sinks A collection of sinks
     * @return A MetricsSink
     */
    public static MetricsSink aggregateMetricsSinks(Collection<? extends MetricsSink> sinks) {
        return new MetaMetricsSink(sinks);
    }
}
