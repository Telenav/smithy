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

import com.mastfrog.bunyan.java.v2.Log;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHooks;
import com.telenav.periodic.metrics.MetricsSink;
import java.time.Duration;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Generic implementation of AbstractLoggingProbe.
 *
 * @author Tim Boudreau
 */
final class DefaultLoggingProbe<Op extends Enum<Op>> extends AbstractLoggingProbe<Op> {

    private final LoggingProbeConfiguration config;

    @Inject
    @SuppressWarnings("unchecked")
    public DefaultLoggingProbe(@Named(value = "ops") Logs logs, ShutdownHooks hooks,
            LoggingProbeConfiguration config,
            Settings settings, MetricsSink sink, BiConsumer<Enum<?>, Duration> opMetrics) {
        // BiConsumer<Enum<?>, Duration> is bound by VertxMetricsModule to the same 
        // instance as BiConsumer<Op, Duration> - Guice cannot handle not-fully-reified
        // injection bindings, so we need to parameterize on Enum<?> here
        super(logs, hooks, settings, sink, (BiConsumer<Op, Duration>) opMetrics);
        this.config = config;
    }

    @Override
    protected void onStartup(Log log) {
        config.onStartupLog(log);
    }

    @Override
    protected String maybeObfuscate(String settingsKey, String settingsValue) {
        return config.obfuscate(settingsKey, settingsValue);
    }

    @Override
    protected String[] configurationKeysToLog() {
        return config.configurationKeysToLog();
    }

    @Override
    public int ordinal() {
        return 0;
    }

    @Override
    protected Loggability loggability(Throwable th) {
        return config.loggability(th);
    }
}
