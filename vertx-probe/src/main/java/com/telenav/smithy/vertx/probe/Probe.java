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

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import static java.lang.System.currentTimeMillis;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Delegating ProbeImplementation, which may have a collection of
 * ProbeImplementations it calls, either synchronously or asynchronously.
 *
 * @author Tim Boudreau
 */
public class Probe<Ops extends Enum<Ops>> implements ProbeImplementation<Ops> {

    static final String START_KEY = "s" + ThreadLocalRandom.current().nextLong();
    static final String AGE_KEY = "a" + ThreadLocalRandom.current().nextLong();

    public static <Ops extends Enum<Ops>> Probe<Ops> create(Collection<? extends ProbeImplementation<? super Ops>> impls) {
        if (impls.isEmpty()) {
            return new NoOpProbe<>();
        }
        return new DelegatingProbe<>(impls);
    }

    /**
     * Get the duration a request has taken to fully respond. If the request has
     * ended, the result will be the amount of time prior to an end handler
     * being called on it; otherwise, if it is still open, this is the duration
     * since it was first seen in a call to Probe.attachTo().
     * <p>
     * If Probe.attachTo() was never called on the context, the result is
     * meaningless and will likely be zero.
     * </p><p>
     * This is handled explicitly here, so that asynchronously run probes will
     * report the correct amount of time even if they are run some time later
     * than the response being completed.
     *
     * @param ctx A routing context
     * @return A duration
     */
    public static Duration durationOf(RoutingContext ctx) {
        Long age = ctx.get(AGE_KEY);
        if (age == null) {
            Long start = ctx.get(START_KEY, 0L);
            return Duration.ofMillis(currentTimeMillis() - start);
        }
        return Duration.ofMillis(age);
    }

    static void fixAge(RoutingContext ctx) {
        Long age = ctx.get(AGE_KEY);
        if (age == null) {
            Long start = ctx.get(START_KEY, 0L);
            long elapsed = currentTimeMillis() - start;
            ctx.put(AGE_KEY, elapsed);
        }
    }

    public static <Ops extends Enum<Ops>> Probe<Ops> empty() {
        return new NoOpProbe<>();
    }

    @Override
    public final int ordinal() {
        return Integer.MAX_VALUE;
    }

    @Override
    public final int compareTo(ProbeImplementation<?> o) {
        return ProbeImplementation.super.compareTo(o);
    }

    /**
     * Wrap this instance in one which will have near-zero overhead within the
     * request cycle.
     *
     * @return a wrapper for this or this if it is already an instance of
     * AsyncProbe.
     */
    public final Probe<Ops> async() {
        if (this instanceof AsyncProbe<?> || this instanceof NoOpProbe<?>) {
            return this;
        }
        return new AsyncProbe<>(this);
    }

    /**
     * Attaches end and body-end handlers to the context which will call this
     * probe on operation completion, failure and/or response body end.
     *
     * @param ctx A routing context
     * @param operation The operation in question
     */
    public final void attachTo(RoutingContext ctx, Ops operation) {
        long start = currentTimeMillis();
        ctx.put(START_KEY, start);
        onStartRequest(operation, ctx);
        ctx.addBodyEndHandler(new ResponseWrittenHandler<>(operation, this, ctx));
        ctx.addEndHandler(new EndRequestResponseCycleHandler<>(operation, this, ctx));
    }

    public <T> Future<T> listen(String failureMessage, Future<T> fut) {
        return fut.andThen(result -> {
            if (result.cause() != null) {
                onNonOperationFailure(failureMessage, result.cause());
            }
        });
    }

    public <T> Future<T> listen(Ops op, RoutingContext ctx, Future<T> fut) {
        return fut.andThen(result -> {
            if (result.cause() != null) {
                this.onFailure(op, ctx, result.cause());
            }
        });
    }

    /**
     * Shut down this probe, terminating any background threads managing
     * emitting things.
     *
     * @return true if the state of something changed as a result of this call
     * @throws InterruptedException
     */
    public boolean shutdown() throws InterruptedException {
        return false;
    }
}
