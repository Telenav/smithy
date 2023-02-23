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

import com.mastfrog.settings.Settings;
import com.telenav.periodic.metrics.MetricsSink;
import io.vertx.core.VertxOptions;
import io.vertx.core.metrics.MetricsOptions;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Attaches our implementation of metrics to the Vertx instance.
 *
 * @author Tim Boudreau
 */
final class VertxMetricsCustomizer implements UnaryOperator<VertxOptions> {

    private final Provider<MetricsSink> sink;
    private final Settings settings;
    private final ClientTimingConsumer clientTimings;

    @Inject
    VertxMetricsCustomizer(Settings settings, Provider<MetricsSink> sink,
            ClientTimingConsumer clientTimings) {
        this.sink = sink;
        this.settings = settings;
        this.clientTimings = clientTimings;
    }

    @Override
    public VertxOptions apply(VertxOptions vxopts) {
        if (!Boolean.getBoolean("unit.test")) {
            MetricsOptions mo = new MetricsOptions();
            mo.setEnabled(true);
            mo.setFactory(new PeriodicVertxMetrics(settings, sink.get(), clientTimings));
            return vxopts.setMetricsOptions(mo);
        }
        return vxopts;
    }

}
