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

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import java.util.Collection;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class DelegatingProbe<Ops extends Enum<Ops>> extends AbstractProbe<Ops> {

    DelegatingProbe(Collection<? extends ProbeImplementation<? super Ops>> l) {
        super(l);
    }

    @Override
    public void onStartup() {
        eachDelegate(ProbeImplementation::onStartup);
    }

    @Override
    public void onShutdown() {
        eachDelegate(ProbeImplementation::onShutdown);
    }

    @Override
    public void onEnterHandler(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        eachDelegate(del -> {
            del.onEnterHandler(op, event, handler);
        });
    }

    @Override
    public void onPayloadRead(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        eachDelegate(del -> del.onPayloadRead(op, event, handler, buffer));
    }

    @Override
    public void onSendResponse(Ops op, RoutingContext event, int status, Optional<?> payload) {
        eachDelegate(del -> del.onSendResponse(op, event, status, payload));
    }

    @Override
    public void onFailure(Ops op, RoutingContext event, Throwable thrown) {
        eachDelegate(del -> del.onFailure(op, event, thrown));
    }

    @Override
    public void onMetric(Ops op, RoutingContext event, String name, Number value) {
        eachDelegate(del -> del.onMetric(op, event, name, value));
    }

    @Override
    public void onEvent(Ops op, String event) {
        eachDelegate(del -> del.onEvent(op, event));
    }

    @Override
    public void onNonOperationFailure(String message, Throwable thrown) {
        eachDelegate(del -> del.onNonOperationFailure(message, thrown));
    }

    public void onResponseCompleted(RoutingContext event, int status, Optional<?> payload) {
        eachDelegate(del -> del.onResponseCompleted(event, status, payload));
    }

    @Override
    public boolean shutdown() throws InterruptedException {
        boolean result = false;
        for (ProbeImplementation<? super Ops> d : delegates) {
            if (d instanceof Probe<?>) {
                result |= ((Probe<?>) d).shutdown();
            }
        }
        return result;
    }

}
