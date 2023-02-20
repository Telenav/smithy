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

import com.telenav.periodic.metrics.OutboundMetricsSink;
import com.telenav.vertx.guice.VertxGuiceModule;

/**
 * Binds metrics support and configures a VertxGuiceModule to use it.
 *
 * @author Tim Boudreau
 */
public final class VertxMetricsSupport<Op extends Enum<Op>> {

    public static final String SETTINGS_KEY_REQUESTS_PER_SECOND = "req.per.second.target";
    public static final String SETTINGS_KEY_MAX_STATS_BUCKETS = "max.stats.buckets";

    private final Class<Op> opType;
    private final Class<? extends OutboundMetricsSink> sinkType;
    private Class<? extends OperationWeights> opWeights;

    public VertxMetricsSupport(Class<Op> opType, Class<? extends OutboundMetricsSink> sinkType) {
        this.opType = opType;
        this.sinkType = sinkType;
    }
    
    public VertxMetricsSupport<Op> withOperationWeights(Class<? extends OperationWeights> weights) {
        this.opWeights= weights;
        return this;
    }

    public static <Op extends Enum<Op>> VertxMetricsSupport<Op> vertxMetricsSupport(Class<Op> opType,
            Class<? extends OutboundMetricsSink> sinkType) {
        return new VertxMetricsSupport<>(opType, sinkType);
    }

    public VertxGuiceModule attachTo(VertxGuiceModule module) {
        module.withVertxOptionsCustomizer(VertxMetricsCustomizer.class);
        module.withModule(new VertxPeriodicMetricsModule<>(opType, sinkType, opWeights));
        return module;
    }
}
