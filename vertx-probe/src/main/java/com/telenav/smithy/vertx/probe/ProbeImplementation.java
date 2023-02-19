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
package com.telenav.smithy.vertx.probe;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Callback interface which can be used for logging or detailed debug info. A
 * few things to note: Where practical, objects passed to a ProbeImplementation
 * are copies of the originals; for some things, such as large buffers, that is
 * not practical. Do not mutate things.
 * <p>
 * ProbeImplementation instances which are bound <i>by type</i> may have objects
 * injected into them, but they will be instantiated exactly once and reused for
 * the life of the application. If you need access to scoped types, request that
 * Providers, not concrete instances, be injected.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface ProbeImplementation<Ops extends Enum<Ops>> extends Comparable<ProbeImplementation<?>> {

    /**
     * Returns a probe implementation which simply logs to stderr, suitable for
     * use in early development of a server.
     *
     * @param <Ops> A type
     * @return a ProbeImplementation
     */
    public static <Ops extends Enum<Ops>> ProbeImplementation<Ops> stderr() {
        return new SystemErrProbe<>();
    }

    default void onStartup() {
        // do nothing
    }

    default void onShutdown() {
        // do nothing
    }

    default void onLaunched(Verticle verticle, String msg) {
        // do nothing
    }

    default void onLaunchFailure(Verticle verticle, DeploymentOptions opts, Throwable thrown) {
        // do nothing
    }

    default void onStartRequest(Ops op, RoutingContext event) {
        // do nothing
    }

    default void onEnterHandler(Ops op, RoutingContext event,
            Class<? extends Handler<RoutingContext>> handler) {
        // do nothing
    }

    default void onBeforePayloadRead(Ops op, RoutingContext event,
            Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        // do nothing
    }

    default void onAfterPayloadRead(Ops op, RoutingContext event,
            Class<? extends Handler<RoutingContext>> handler, Optional<?> payload) {
        // do nothing
    }

    default void onBeforeSendResponse(Ops op, RoutingContext event, Optional<?> payload) {
        // do nothing
    }

    default void onAfterSendResponse(Ops op, RoutingContext event, int statusCode) {
        // do nothing
    }

    default void onResponseCompleted(Ops op, RoutingContext event, int status) {
        // do nothing
    }

    default void onFailure(Ops op, RoutingContext event, Throwable thrown) {
        // do nothing
    }

    default void onMetric(Ops op, RoutingContext event, String name, Number value) {
        // do nothing
    }

    /**
     * Pass some adhoc information to the probe - typically something that
     * should be logged, without forcing a dependency on a particular logging
     * framework.
     *
     * @param op An optional operation - may be null
     * @param event The name of the event
     * @param payload The event payload or null
     */
    default void onEvent(String event, Object payload) {
        onEvent(null, event, payload);
    }

    /**
     * Pass some adhoc information to the probe - typically something that
     * should be logged, without forcing a dependency on a particular logging
     * framework.
     *
     * @param op An optional operation - may be null
     * @param event The name of the event
     * @param payload The event payload or null
     */
    default void onEvent(Ops op, String event, Object payload) {
        // do nothing
    }

    /**
     * Call with failures during startup or background jobs, outside the event
     * loop.
     *
     * @param message A message
     * @param thrown A throwable
     */
    default void onNonOperationFailure(String message, Throwable thrown) {
        // do nothing
    }

    default int ordinal() {
        return 0;
    }

    @Override
    default int compareTo(ProbeImplementation<?> o) {
        return Integer.compare(ordinal(), o.ordinal());
    }

    /**
     * Vertx has a race in SparseArray.put(), resulting in a stack like
     * <pre>
     * java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3
     *  at io.vertx.ext.web.impl.SparseArray.put(SparseArray.java:54)
     *  at io.vertx.ext.web.impl.RoutingContextImpl.addEndHandler(RoutingContextImpl.java:399)
     *  at io.vertx.ext.web.RoutingContext.addEndHandler(RoutingContext.java:510)
     *  at com.telenav.smithy.safety.service.vertx.launcher.logging.LoggingProbe.onStartRequest(LoggingProbe.java:202)
     * </pre>
     * <p>
     * We attempt a dirty workaround, yielding the current thread, then
     * synchronizing on the context to create a memory barrier, retrying, and if
     * that fails, pushing one subsequent retry onto the netty event loop.
     * </p>
     *
     * @param <T> The handler's target type
     * @param c A handle to the method that accepts a handler
     * @param handler A handler
     * @param lockable The context
     * @return True if adding the handler succeeded in the current thread
     */
    static <T> boolean aioobeWorkaroundAddHandler(Consumer<Handler<T>> c, Handler<T> handler, RoutingContext lockable) {
        return Probe.aioobeWorkaroundAddHandler(c, handler, lockable);
    }
}
