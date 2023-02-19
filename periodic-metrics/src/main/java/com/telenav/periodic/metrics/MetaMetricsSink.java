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

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates a list of metrics sinks and distributes inbound metrics to all of
 * them - used so that we can have multiple MetricsSink instances which are
 * polled on different intervals and can reset their contents when they are
 * polled.
 *
 * @author Tim Boudreau
 */
final class MetaMetricsSink implements MetricsSink {

    private final List<MetricsSink> subsinks = new ArrayList<>();

    MetaMetricsSink(Iterable<? extends MetricsSink> it) {
        it.forEach(subsinks::add);
    }

    MetaMetricsSink() {

    }

    void add(MetricsSink sink) {
        subsinks.add(sink);
    }

    @Override
    public void onMetric(Metric kind, long amount) {
        for (MetricsSink sub : subsinks) {
            sub.onMetric(kind, amount);
        }
    }
    
    @Override
    public String toString() {
        return "MetaMetricsSink(" + subsinks + ")";
    }

}
