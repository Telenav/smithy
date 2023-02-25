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
import com.telenav.periodic.metrics.BuiltInMetrics;
import com.telenav.periodic.metrics.MetricsSink;
import static com.telenav.smithy.vertx.bunyan.logging.BunyanLoggingAndMetricsSupport.DEFAULT_DUMP_STACKS_TO_SYSTEM_ERR;
import static com.telenav.smithy.vertx.bunyan.logging.BunyanLoggingAndMetricsSupport.DEFAULT_EXIT_ON_VERTICLE_FAILURE;
import static com.telenav.smithy.vertx.bunyan.logging.BunyanLoggingAndMetricsSupport.SETTINGS_KEY_DUMP_STACKS_TO_SYSTEM_ERR;
import static com.telenav.smithy.vertx.bunyan.logging.BunyanLoggingAndMetricsSupport.SETTINGS_KEY_EXIT_ON_VERTICLE_LAUNCH_FAILULRE;
import com.telenav.smithy.vertx.probe.Probe;
import com.telenav.smithy.vertx.probe.ProbeImplementation;
import com.telenav.vertx.guice.scope.RequestScope;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import javax.inject.Named;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractLoggingProbe<Op extends Enum<Op>> implements ProbeImplementation<Op> {

    protected static final Pattern PW_PATTERN = Pattern.compile("(\\S+):\\/\\/(\\S+):(\\S+)@(\\S+)");
    protected final Logs logs;
    protected final Settings settings;
    protected final MetricsSink sink;
    protected final BiConsumer<Op, Duration> opMetrics;
    protected final boolean dumpStacks;
    protected final boolean exitOnVerticleFailure;
    protected final Runnable onShutdown = this::shuttingDown;
    protected volatile boolean shuttingDown;

    protected AbstractLoggingProbe(@Named(value = "ops") Logs logs, ShutdownHooks hooks,
            Settings settings, MetricsSink sink, BiConsumer<Op, Duration> opMetrics) {
        hooks.addFirst(onShutdown);
        this.logs = logs;
        this.sink = sink;
        this.settings = settings;
        this.dumpStacks = settings.getBoolean(SETTINGS_KEY_DUMP_STACKS_TO_SYSTEM_ERR,
                DEFAULT_DUMP_STACKS_TO_SYSTEM_ERR);
        this.exitOnVerticleFailure = settings.getBoolean(SETTINGS_KEY_EXIT_ON_VERTICLE_LAUNCH_FAILULRE,
                DEFAULT_EXIT_ON_VERTICLE_FAILURE);
        this.opMetrics = opMetrics;
    }

    protected void shuttingDown() {
        shuttingDown = true;
    }

    /**
     * Obfuscate logged settings which may contain credentials.
     *
     * @param settingsKey The setting
     * @param settingsValue The raw value
     * @return The passed settingsValue, obfuscated if that is needed
     */
    protected abstract String maybeObfuscate(String settingsKey, String settingsValue);

    protected abstract String[] configurationKeysToLog();

    protected Map<String, String> configuration() {
        Map<String, String> result = new TreeMap<>();
        for (String key : configurationKeysToLog()) {
            String val = settings.getString(key);
            if (val != null) {
                result.put(key, maybeObfuscate(key, val));
            }
        }
        String logDir = System.getProperty(Logging.SETTINGS_KEY_LOG_DIR);
        if (logDir != null) {
            result.put(Logging.SETTINGS_KEY_LOG_DIR, logDir);
        }
        return result;
    }

    @Override
    public final void onStartup() {
        logs.warn("startup", log -> {
            log.add("os", System.getProperty("os.name"))
                    .add("osVer", System.getProperty("os.version"))
                    .add("osArch", System.getProperty("os.arch"))
                    .add("javaVersion", System.getProperty("java.version"))
                    .add("configuration", configuration());
            onStartup(log);
        });
    }

    protected void onStartup(Log log) {

    }

    @Override
    public final void onShutdown() {
        logs.info("shutdown").close();
    }

    @Override
    public final void onLaunched(Verticle verticle, String msg) {
        logs.info("launchVerticle").add("verticle", msg).close();
    }

    @Override
    public final void onLaunchFailure(Verticle verticle, DeploymentOptions opts, Throwable thrown) {
        logs.info("launchVerticleFailed").add("verticle", verticle.getClass().getName()).add("opts", opts.toJson()).add(thrown).close();
        if (dumpStacks) {
            thrown.printStackTrace(System.err);
        }
        sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
        if (exitOnVerticleFailure) {
            System.exit(1);
        }
    }

    private void includeEventInfo(RoutingContext event, Log log) {
        try (log) {
            log.add("uri", event.request().uri()).add("method", event.request().method().name());
            Object reqId = event.get(Probe.REQUEST_ID_KEY);
            if (reqId != null) {
                log.add("requestId", reqId);
            }
        }
    }

    private void includeRequestId(RoutingContext event, Log log) {
        try (log) {
            Object reqId = event.get(Probe.REQUEST_ID_KEY);
            if (reqId != null) {
                log.add("requestId", reqId);
            }
        }
    }

    private String loggingNameOf(Op op) {
        return op.name().toLowerCase().replace('_', '-');
    }

    @Override
    public final void onStartRequest(Op op, RoutingContext event) {
        includeEventInfo(event, logs.trace("startRequest").add("op", loggingNameOf(op)));
    }

    @Override
    public final void onEnterHandler(Op op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        includeEventInfo(event, logs.debug("enter").add("op", loggingNameOf(op)).add("in", handler.getSimpleName()));
    }

    @Override
    public final void onBeforePayloadRead(Op op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        includeRequestId(event, logs.debug("beforePayloadRead").add("in", handler.getSimpleName()).add("bytes", buffer.length()));
    }

    @Override
    public final void onAfterPayloadRead(Op op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Optional<?> payload) {
        logs.trace("readInbound", log -> {
            payload.ifPresent(pay -> log.add("payload", pay));
            includeRequestId(event, log.add("op", loggingNameOf(op)).add("hasPayload", payload.isPresent()));
        });
    }

    @Override
    public final void onBeforeSendResponse(Op op, RoutingContext event, Optional<?> payload) {
        logs.trace("sendResponse", log -> {
            payload.ifPresent(pay -> log.add("payload", pay));
            includeRequestId(event, log.add("op", loggingNameOf(op)).add("hasPayload", payload.isPresent()));
        });
    }

    @Override
    public final void onAfterSendResponse(Op op, RoutingContext event, int status) {
        includeRequestId(event, logs.debug("afterSendResponse").add("op", loggingNameOf(op)).add("status", status));
    }

    @Override
    public final void onResponseCompleted(Op op, RoutingContext event, int status) {
        Duration dur = Probe.durationOf(event);
        includeRequestId(event, logs.info("completed").add("op", loggingNameOf(op)).add("status", status).add("duration", dur));
        opMetrics.accept(op, dur);
    }

    @Override
    public final void onFailure(Op op, RoutingContext event, Throwable thrown) {
        loggabilityInternal(thrown).apply("failure", logs, thrown, log -> {
            sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
            log.add("op", loggingNameOf(op));
            SocketAddress addr = event.request().remoteAddress();
            if (addr != null) {
                log.add("address", addr.toString());
            }
            if (dumpStacks) {
                thrown.printStackTrace(System.err);
            }
        });
    }

    @Override
    public final void onMetric(Op op, RoutingContext event, String name, Number value) {
        includeRequestId(event, logs.info("metric").add("op", loggingNameOf(op)).add(name, value));
    }

    @Override
    public final void onEvent(Op op, String event, Object payload) {
        logs.warn(event, log -> {
            if (op != null) {
                log.add("op", loggingNameOf(op));
            }
            log.add(payload);
        });
    }

    @Override
    public final void onNonOperationFailure(String message, Throwable thrown) {
        loggabilityInternal(thrown).apply("failure", logs, thrown, log -> {
            sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
            if (dumpStacks) {
                thrown.printStackTrace(System.err);
            }
        });
    }

    protected final Loggability loggabilityInternal(Throwable thrown) {
        // Things that can only be programmer error must always be logged
        if (thrown instanceof NullPointerException || thrown instanceof ClassCastException
                || thrown instanceof StackOverflowError
                || thrown.getCause() instanceof NullPointerException || thrown.getCause() instanceof ClassCastException
                || thrown.getCause() instanceof StackOverflowError) {
            return Loggability.ERROR;
        }
        if (shuttingDown) {
            // We can get a flurry of exceptions during shutdown - ignore
            return Loggability.NONE;
        }
        if (thrown instanceof ClosedChannelException || thrown instanceof HttpClosedException) {
            return Loggability.DEBUG_NOSTACK;
        }
        if (thrown instanceof IOException && ("Connection reset by peer".equals(thrown.getMessage()) || "Broken pipe".equals(thrown.getMessage()))) {
            return Loggability.DEBUG_NOSTACK;
        }
        if (thrown instanceof NoStackTraceThrowable && "Pool closed".equals(thrown.getMessage())) {
            return Loggability.DEBUG_NOSTACK;
        }
        return loggability(thrown);
    }

    protected Loggability loggability(Throwable th) {
        return Loggability.ERROR;
    }

}
