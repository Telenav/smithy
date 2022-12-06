/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.telenav.smithy.vertx.probe;

import com.google.inject.Provider;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;

/**
 * Callback interface which can be used for logging or detailed debug info.
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

    default void onEnterHandler(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        // do nothing
    }

    default void onPayloadRead(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        // do nothing
    }

    default void onSendResponse(Ops op, RoutingContext event, int status, Optional<?> payload) {
        // do nothing
    }

    default void onResponseCompleted(RoutingContext event, int status, Optional<?> payload) {
        // do nothing
    }

    default void onFailure(Ops op, RoutingContext event, Throwable thrown) {
        // do nothing
    }

    default void onMetric(Ops op, RoutingContext event, String name, Number value) {
        // do nothing
    }

    default void onEvent(Ops op, String event) {
        // do nothing
    }

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
}
