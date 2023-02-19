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
import java.util.function.BiConsumer;

/**
 * Metrics producer which may emit multiple metrics, which typically gets its
 * values from a single atomic snapshot of its internal state - for example,
 * statistical values derived from duration of requests, where a single
 * collection of data is used to derive all the necessary values and then reset.
 *
 * @author Tim Boudreau
 */
public interface MultiMetric<N extends Number> {

    /**
     * Get amount of metrics.
     *
     * @param c A consumer to accept the values
     * @return whether or not any metrics were emitted.
     */
    boolean get(BiConsumer<Metric, N> c);

    void add(long value);

    default void addTime(Duration time) {
        add(time.toMillis());
    }
}
