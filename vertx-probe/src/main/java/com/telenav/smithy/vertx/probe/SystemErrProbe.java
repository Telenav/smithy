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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import static java.lang.System.currentTimeMillis;
import java.time.Duration;
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
        StringBuilder sb = new StringBuilder(30);
        long elapsed = currentTimeMillis() - startup;
        formatElapsed(elapsed, sb);

        int lenDiff = 19 - sb.length();
        if (lenDiff > 0) {
            char[] c = new char[lenDiff];
            Arrays.fill(c, ' ');
            sb.insert(0, c);
        }
        return sb.append('\t').toString();
    }

    private static void formatElapsed(long elapsed, StringBuilder sb) {
        // Quick'n'dirty duration format in the form
        // hours:minutes:seconds.millis
        long mins = elapsed / (60 * 1000);
        long hours = elapsed / (60 * 60 * 1000);
        long seconds = elapsed / 1000;
        long millis = elapsed % 1000;
        if (hours > 0) {
            String s = Long.toString(hours);
            if (s.length() == 1) {
                sb.append('0');
            }
            sb.append(s);
        }
        if (mins > 0 || hours > 0) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            String s = Long.toString(mins % 60);
            if (s.length() == 1) {
                sb.append('0');
            }
            sb.append(s);
        }
        if (seconds > 0 || mins > 0 || hours > 0) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            String s = Long.toString(seconds % 60);
            if (s.length() == 1) {
                sb.append('0');
            }
            sb.append(s);
        } else {
            sb.append('0');
        }
        sb.append('.');
        String ms = Long.toString(millis);
        for (int i = 0; i < 3 - ms.length(); i++) {
            sb.append('0');
        }
        sb.append(millis);
    }

    private void emit(String what) {
        System.err.println("┇" + elapsed() + what);
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
    public void onLaunched(Verticle verticle, String msg) {
        emit("launched\t" + msg + "\t" + verticle);
    }

    @Override
    public void onLaunchFailure(Verticle verticle, DeploymentOptions opts, Throwable thrown) {
        emit("launchFailure\n" + thrown);
        thrown.printStackTrace(System.err);
    }

    @Override
    public void onStartRequest(O op, RoutingContext event) {
        emit("start\t" + op.name() + "\t" + event.request().uri()
                + "\t" + event.request().remoteAddress());
    }

    @Override
    public void onEnterHandler(O op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        emit("enter\t" + op.name() + "\t" + handler.getSimpleName() + "\t" + event.request().uri());
    }

    @Override
    public void onBeforePayloadRead(O op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
        emit("read\t" + buffer.length() + "\t" + op.name() + "\t" + handler.getSimpleName() + "\t" + event.request().uri());
    }

    @Override
    public void onAfterPayloadRead(O op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Optional<?> buffer) {
        emit("read\t" + buffer.isPresent() + "\t" + op.name() + "\t" + handler.getSimpleName() + "\t" + event.request().uri());
    }

    @Override
    public void onBeforeSendResponse(O op, RoutingContext event, Optional<?> payload) {
        emit("responding\t" + op.name() + "\t" + event.request().uri() + "\t" + payload.map(obj -> obj.getClass().getSimpleName()).orElse("-empty-"));
    }

    @Override
    public void onAfterSendResponse(O op, RoutingContext event, int status) {
        emit("responded\t" + op.name() + "\t" + event.request().uri() + "\t" + status);
    }

    @Override
    public void onResponseCompleted(O op, RoutingContext event, int status) {
        emit("completed\t" + op.name() + "\t" + event.request().uri() + "\t" + status + "\tduration " + ageOf(event));
    }

    private String ageOf(RoutingContext event) {
        Duration dur = Probe.durationOf(event);
        StringBuilder sb = new StringBuilder();
        formatElapsed(dur.toMillis(), sb);
        return sb.toString();
    }

    @Override
    public void onFailure(O op, RoutingContext event, Throwable thrown) {
        emit("failure\t" + op.name() + "\t" + event.request().uri() + "\tduration " + ageOf(event) + "\t" + thrown);
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
