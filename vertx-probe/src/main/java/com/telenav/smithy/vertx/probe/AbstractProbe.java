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

import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.sort;
import java.util.List;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
abstract class AbstractProbe<Ops extends Enum<Ops>> extends Probe<Ops> {

    final List<? extends ProbeImplementation<? super Ops>> delegates;

    AbstractProbe(Collection<? extends ProbeImplementation<? super Ops>> l) {
        delegates = new ArrayList<>(l);
        sort(delegates);
    }

    void eachDelegate(Consumer<? super ProbeImplementation<? super Ops>> c) {
        delegates.forEach(delegate -> {
            try {
                c.accept(delegate);
            } catch (Exception | Error ex) {
                // We are already IN the logging mechanism here - do not try to
                // do anything that can call us back reentrantly
                ex.printStackTrace(System.err);
            }
        });
    }

}
