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
import java.time.temporal.ChronoUnit;

/**
 * The set of supported metrics emission periods.
 *
 * @author Tim Boudreau
 */
public enum StandardMetricsPeriods {
    ONE_MINUTE, FIVE_MINUTE, HOURLY, FOUR_HOUR;

    public Duration toDuration() {
        return interval().duration();
    }

    IntervalSpec interval() {
        switch (this) {
            case ONE_MINUTE:
                return new IntervalSpec(ChronoUnit.MINUTES, 1);
            case FIVE_MINUTE:
                return new IntervalSpec(ChronoUnit.MINUTES, 5);
            case HOURLY:
                return new IntervalSpec(ChronoUnit.HOURS, 1);
            case FOUR_HOUR:
                return new IntervalSpec(ChronoUnit.HOURS, 4);
            default:
                throw new AssertionError(this);
        }
    }

    static StandardMetricsPeriods valueFor(IntervalSpec spec) {
        for (StandardMetricsPeriods p : StandardMetricsPeriods.values()) {
            if (spec.equals(p.interval())) {
                return p;
            }
        }
        throw new IllegalArgumentException(spec + " does not match and standard period");
    }

    ClockInterval<SimpleMetricsSink> create(MetricsRegistry.MetricsRegistrar registrar, boolean omitGcMetrics) {
        MetricsSinks.MetricsMapFactory factory = new MetricsSinks.MetricsMapFactory(registrar.metrics());
        String name = name().toLowerCase().replace('_', '-');
        IntervalSpec spec = interval();
        SimpleMetricsSink sink = new SimpleMetricsSink(name, factory);
        if (!omitGcMetrics) {
            sink.includeGcMetrics();
        }
        sink.addMultiMetrics(registrar.multiMetrics(spec.duration()));
        ClockInterval<SimpleMetricsSink> result = new ClockInterval<>(spec, sink);
        return result;
    }

}
