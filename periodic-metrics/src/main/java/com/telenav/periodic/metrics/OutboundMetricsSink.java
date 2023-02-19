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
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Destination for metrics - the thing that actually logs or transmits them.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(NoOpOutboundMessageSink.class)
public interface OutboundMetricsSink {

    /**
     * Emit a batch of metrics to whatever logger or destination metrics are
     * emitted to.
     *
     * @param batchInterval The interval for which this batch is being emitted
     * @param msg The type of metrics, e.g. one-minute-metrics.
     * @param started WHether or not the metrics set is "started" - if the
     * metrics system was started between intervals, and a complete interval has
     * not yet passed, then this value will be false - there will be metrics,
     * but they do not represent metrics for a complete interval
     * @param c A consumer which is to be passed a BiConsumer which will accept
     * the contents of this batch of metrics
     */
    void batch(Duration batchInterval, String msg,
            boolean started, Consumer<BiConsumer<Metric, Long>> c);
}
