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

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import static java.lang.System.currentTimeMillis;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Delegating ProbeImplementation, which may have a collection of
 * ProbeImplementations it calls, either synchronously or asynchronously.
 *
 * @author Tim Boudreau
 */
public class Probe<Ops extends Enum<Ops>> implements ProbeImplementation<Ops> {

    static final String START_KEY = "s" + ThreadLocalRandom.current().nextLong();
    static final String AGE_KEY = "a" + ThreadLocalRandom.current().nextLong();
    /**
     * Key for use with the stderr probe - any object stored in the request
     * context under this key will be included in log messages.
     */
    public static final String REQUEST_ID_KEY = "requestId";

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
        try {
            onStartRequest(operation, ctx);
        } finally {
            /*  Some sort of race bug in vertx:
              java.lang.ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 2
                at io.vertx.ext.web.impl.SparseArray.put(SparseArray.java:54)
                at io.vertx.ext.web.impl.RoutingContextImpl.addEndHandler(RoutingContextImpl.java:399)
                at com.telenav.smithy.vertx.probe.Probe.attachTo(Probe.java:122) */
            aioobeWorkaroundAddHandler(ctx::addBodyEndHandler, new ResponseWrittenHandler<>(operation, this, ctx), ctx);
            aioobeWorkaroundAddHandler(ctx::addEndHandler, new EndRequestResponseCycleHandler<>(operation, this, ctx), ctx);
        }
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

    // Workaround for a race condition in vertx
    static <T> boolean aioobeWorkaroundAddHandler(Consumer<Handler<T>> c, Handler<T> handler, RoutingContext lockable) {
        return aioobeWorkaroundAddHandler(c, handler, lockable, false);
    }

    @SuppressWarnings("CallToThreadYield")
    private static <T> boolean aioobeWorkaroundAddHandler(Consumer<Handler<T>> c, Handler<T> handler, RoutingContext lockable, boolean recursing) {
        try {
            c.accept(handler);
            return true;
        } catch (ArrayIndexOutOfBoundsException aioobe) {
            try {
                Thread.yield();
                synchronized (lockable) {
                    c.accept(handler);
                    return true;
                }
            } catch (ArrayIndexOutOfBoundsException aioobe2) {
                aioobe2.addSuppressed(aioobe);
                aioobe2.printStackTrace(System.err);
                if (!recursing) {
                    lockable.vertx().nettyEventLoopGroup().submit(() -> {
                        aioobeWorkaroundAddHandler(c, handler, lockable, true);
                    });
                }
            }
        }
        return false;
    }
}
