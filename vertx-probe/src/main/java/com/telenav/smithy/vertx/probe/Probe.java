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

import java.util.Collection;

/**
 * Delegating ProbeImplementation, which may have a collection of
 * ProbeImplementations it calls, either synchronously or asynchronously.
 *
 * @author Tim Boudreau
 */
public class Probe<Ops extends Enum<Ops>> implements ProbeImplementation<Ops> {

    public static <Ops extends Enum<Ops>> Probe<Ops> create(Collection<? extends ProbeImplementation<? super Ops>> impls) {
        if (impls.isEmpty()) {
            return new NoOpProbe<>();
        }
        return new DelegatingProbe<>(impls);
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

    public boolean shutdown() throws InterruptedException {
        return false;
    }
}
