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

import java.util.Optional;

/**
 * Metric enum for use (generally) in combination with generated operation
 * enums, to collect metrics for individual HTTP operations. Percentiles may be
 * computed using any of the methods on PercentileMethod.
 */
public enum StatisticalMetrics implements Metric {
    /**
     * The statistical average.
     */
    MEAN,
    /**
     * The median, or p50 middle value
     */
    MEDIAN,
    /**
     * The minimum value encountered.
     */
    MIN,
    /**
     * The maximum value encountered.
     */
    MAX,
    /**
     * The 90th percentile value encountered.
     */
    P90,
    /**
     * The 99th percentile value encountered.
     */
    P99,
    /**
     * The 10th percentile value encountered.
     */
    P10;

    @Override
    public boolean omitIfNegative() {
        return true;
    }

    @Override
    public boolean omitIfZero() {
        return false;
    }

    @Override
    public boolean isOnDemand() {
        return true;
    }

    /**
     * If this is a percentile metric, returns the percentile as a double
     * between 0 and 1.
     *
     * @return The percentile, if applicable
     */
    public Optional<Double> percentile() {
        switch (this) {
            case MIN:
            case MAX:
            case MEAN:
                return Optional.empty();
            case MEDIAN:
                return Optional.of(0.5);
            case P10:
                return Optional.of(0.1);
            case P90:
                return Optional.of(0.9);
            case P99:
                return Optional.of(0.99);
            default:
                throw new AssertionError(this);
        }
    }
}
