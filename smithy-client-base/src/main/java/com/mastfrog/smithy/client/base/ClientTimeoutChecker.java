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
package com.mastfrog.smithy.client.base;

import static com.mastfrog.smithy.client.base.ClientConfig.debugLog;
import com.mastfrog.smithy.client.state.State;
import static java.lang.Math.max;
import java.lang.ref.WeakReference;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
final class ClientTimeoutChecker {

    private final ClientConfig config;
    private final AtomicBoolean started = new AtomicBoolean();
    private final DelayQueue<WeakDelayedReference<JacksonBodyHandler<?>>> q
            = new DelayQueue<>();

    ClientTimeoutChecker(ClientConfig config) {
        this.config = config;
    }

    void start() {
        config.executor().submit(this::timeoutLoop);
    }

    void track(JacksonBodyHandler<?> handler) {
        if (!started.compareAndSet(false, true)) {
            start();
        }
    }

    private void timeoutLoop() {
        try {
            for (; started.get();) {
                try {
                    WeakDelayedReference<JacksonBodyHandler<?>> ref = q.take();
                    JacksonBodyHandler<?> handler = ref.get();
                    if (handler != null) {
                        if (handler.state().state() != State.DONE) {
                            debugLog("Force timeout for " + handler);
                            handler.checkTimeout();
                        }
                    }
                } catch (InterruptedException ex) {
                    Logger.getLogger(ClientTimeoutChecker.class.getName()).log(Level.SEVERE, null, ex);
                    break;
                }
            }
        } finally {
            started.set(false);
        }
    }

    static class WeakDelayedReference<T extends Delayed> extends WeakReference<T> implements Delayed {

        private final long deadline;

        public WeakDelayedReference(T referent) {
            super(referent);
            long delay = referent.getDelay(TimeUnit.MILLISECONDS);
            long now = System.currentTimeMillis();
            if (delay <= 0) {
                deadline = now;
            } else {
                deadline = now + delay;
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = max(0, deadline - System.currentTimeMillis());
            return unit.convert(remaining, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(getDelay(MILLISECONDS), o.getDelay(MILLISECONDS));
        }

    }

}
