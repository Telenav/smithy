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
import static java.lang.System.currentTimeMillis;
import java.util.Arrays;
import java.util.Optional;

/**
 * A simple probe that logs to System.err, for early development debugging.
 *
 * @author Tim Boudreau
 */
final class SystemErrProbe<O extends Enum<O>> implements ProbeImplementation<O> {

    private final long startup = currentTimeMillis();

    private String elapsed() {
        StringBuilder sb = new StringBuilder();
        long elapsed = System.currentTimeMillis();
        sb.append(Long.toString(elapsed));
        int lenDiff = 12 - sb.length();
        if (lenDiff > 0) {
            char[] c = new char[lenDiff];
            Arrays.fill(c, ' ');
            sb.insert(0, c);
        }
        return sb.append('\t').toString();
    }

    private void emit(String what) {
        System.out.println(elapsed() + what);
    }

    @Override
    public void onStartup() {
        emit("startup");
    }

    @Override
    public void onShutdown() {
        emit("shutdown");
    }

    @Override
    public void onEnterHandler(O op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        emit("enter\t" + op.name() + "\t" + handler.getSimpleName() + "\t" + event.request().uri());
    }

    @Override
    public void onPayloadRead(O op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        emit("read\t" + buffer.length() + "\t" + op.name() + "\t" + handler.getSimpleName() + "\t" + event.request().uri());
    }

    @Override
    public void onSendResponse(O op, RoutingContext event, int status, Optional<?> payload) {
        emit("respond\t" + op.name() + "\t" + event.request().uri() + "\t" + status);
    }

    @Override
    public void onResponseCompleted(RoutingContext event, int status, Optional<?> payload) {
        emit("completed\t" + event.request().uri() + "\t" + status);
    }

    @Override
    public void onFailure(O op, RoutingContext event, Throwable thrown) {
        emit("failure\t" + op.name() + "\t" + event.request().uri() + "\t" + thrown);
        thrown.printStackTrace(System.err);
    }

    @Override
    public void onMetric(O op, RoutingContext event, String name, Number value) {
        emit(name + "\t" + op.name() + "\t" + value + "\t" + event.request().uri());
    }

    @Override
    public void onEvent(O op, String event) {
        emit("event\t" + op.name() + "\t" + event);
    }

    @Override
    public void onNonOperationFailure(String message, Throwable thrown) {
        emit("failure\t" + message + "\t" + thrown);
        thrown.printStackTrace(System.err);
    }

}
