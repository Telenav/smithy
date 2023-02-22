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
package com.telenav.smithy.vertx.bunyan.logging;

import com.telenav.periodic.metrics.OutboundMetricsSink;
import com.mastfrog.bunyan.java.v2.Logs;
import com.telenav.periodic.metrics.Metric;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class LogsOutboundMetricsSink implements OutboundMetricsSink {

    private final Logs logs;

    @Inject
    LogsOutboundMetricsSink(@Named("metrics") Logs logs) {
        this.logs = logs;
    }

    @Override
    public void batch(Duration batchDuration, String msg, boolean started, Consumer<BiConsumer<Metric, Long>> c) {
        logs.warn(msg, log -> {
            log.add("interval", batchDuration);
            c.accept((metric, val) -> {
                if (started) {
                    log.add(metric.loggingName(), val);
                }
            });
        });
    }
}
