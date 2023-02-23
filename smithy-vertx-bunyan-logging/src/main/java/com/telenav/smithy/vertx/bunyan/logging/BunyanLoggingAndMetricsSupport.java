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

import com.mastfrog.giulius.annotations.Setting;
import static com.mastfrog.giulius.annotations.Setting.Tier.TERTIARY;
import static com.mastfrog.giulius.annotations.Setting.ValueType.BOOLEAN;
import com.mastfrog.settings.Settings;
import com.telenav.periodic.metrics.OutboundMetricsSink;
import com.telenav.smithy.vertx.periodic.metrics.VertxMetricsSupport;
import static com.telenav.smithy.vertx.periodic.metrics.VertxMetricsSupport.vertxMetricsSupport;
import com.telenav.smithy.vertx.probe.ProbeImplementation;
import com.telenav.vertx.guice.VertxGuiceModule;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public final class BunyanLoggingAndMetricsSupport<Op extends Enum<Op>> {

    static final boolean DEFAULT_DUMP_STACKS_TO_SYSTEM_ERR = true;
    @Setting(type = BOOLEAN, value = "If true, in addition to logging exceptions via the logger, print "
            + "stack traces to System.err when an unexpected exception is encountered.",
            defaultValue = DEFAULT_DUMP_STACKS_TO_SYSTEM_ERR + "", tier = TERTIARY)
    public static final String SETTINGS_KEY_DUMP_STACKS_TO_SYSTEM_ERR = "system.err.stacks";

    static final boolean DEFAULT_EXIT_ON_VERTICLE_FAILURE = true;
    @Setting(type = BOOLEAN, value = "If true, exit the application with status 1 if the verticle fails "
            + "to launch (typically because another process is squatting the port the server wants to "
            + "open)", defaultValue = DEFAULT_EXIT_ON_VERTICLE_FAILURE + "", tier = TERTIARY)
    public static final String SETTINGS_KEY_EXIT_ON_VERTICLE_LAUNCH_FAILULRE = "exit.on.verticle.failure";

    private boolean collectDbTimings;
    private boolean installLoggingModule = true;
    private boolean installMetricsSupport = true;
    private final Class<Op> opType;
    private final Settings settings;

    public BunyanLoggingAndMetricsSupport(Class<Op> opType, Settings settings) {
        this.opType = opType;
        this.settings = settings;
    }

    public BunyanLoggingAndMetricsSupport<Op> dontInstallLoggingModule() {
        installLoggingModule = false;
        return this;
    }

    public BunyanLoggingAndMetricsSupport<Op> dontInstallMetricsSupport() {
        installMetricsSupport = false;
        return this;
    }

    public BunyanLoggingAndMetricsSupport<Op> collectDbMetrics() {
        collectDbTimings = true;
        return this;
    }

    @SuppressWarnings("unchecked")
    public void attach(VertxGuiceModule mod, Consumer<Class<? super ProbeImplementation<? super Op>>> probeConsumer) {
        probeConsumer.accept((Class) DefaultLoggingProbe.class);
        if (installLoggingModule) {
            mod.withModule(new Logging<>(settings, opType));
        }
        if (installMetricsSupport) {
            VertxMetricsSupport<Op> vms = vertxMetricsSupport(opType, LogsOutboundMetricsSink.class);
            if (collectDbTimings) {
                vms.collectDbTimings();
            }
            vms.attachTo(mod);
        }
    }

    public Class<? extends OutboundMetricsSink> outboundSinkType() {
        return LogsOutboundMetricsSink.class;
    }

}
