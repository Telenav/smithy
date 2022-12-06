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
import static java.util.Arrays.asList;
import java.util.LinkedList;
import java.util.Optional;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Asynchronous implementation of Probe which uses a background thread to call
 * all of the providers it wraps. Ensures all events are written, even when
 * called during shutdown.
 *
 * @author Tim Boudreau
 */
final class AsyncProbe<Ops extends Enum<Ops>> extends AbstractProbe<Ops> {

    private static final long PARK_INTERVAL_SECONDS = 10;
    private static final int STATE_NEW = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_SHUTDOWN = 2;
    private final AtomicInteger state = new AtomicInteger();
    private final AtomicReference<Cell<Ops>> head = new AtomicReference<>();
    private final Thread emitter = new Thread(this::emitLoop, "async-probe");

    AsyncProbe(Probe<Ops> delegate) {
        super(asList(delegate));
        emitter.setPriority(Thread.NORM_PRIORITY - 1);
        emitter.setDaemon(true);
    }

    boolean drain(LinkedList<? super OpRecord<Ops>> into) {
        Cell<Ops> cell = head.getAndSet(null);
        if (cell != null) {
            cell.addTo(into);
            return true;
        }
        return false;
    }

    void push(OpRecord<Ops> op) {
        Cell<Ops> prev = head.getAndUpdate(old -> new Cell<>(op, old));
        if (prev == null) {
            enqueue();
        }
    }

    @Override
    public boolean shutdown() throws InterruptedException {
        int oldState = state.getAndUpdate(old -> STATE_SHUTDOWN);
        if (oldState == STATE_STARTED) {
            if (emitter.isAlive()) {
                LockSupport.unpark(emitter);
            }
            emitter.join();
            return true;
        }
        return false;
    }

    void enqueue() {
        int oldState = state.getAndUpdate(old -> {
            switch (old) {
                case STATE_NEW:
                    return STATE_STARTED;
                default:
                    return old;
            }
        });
        switch (oldState) {
            case STATE_NEW:
                start();
                break;
            case STATE_STARTED:
                LockSupport.unpark(emitter);
                break;
            case STATE_SHUTDOWN:
                emitOneBatch(drainAll(new LinkedList<>()));
                break;
            default:
                throw new AssertionError(oldState);
        }
    }

    void emitLoop() {
        LinkedList<OpRecord<Ops>> records = new LinkedList<>();
        do {
            drainAll(records);
            if (!records.isEmpty()) {
                emitOneBatch(records);
            }
            int lastState = state.get();
            if (lastState != STATE_SHUTDOWN) {
                LockSupport.parkNanos(this, NANOSECONDS.convert(
                        PARK_INTERVAL_SECONDS, SECONDS));
            }
        } while (state.get() != STATE_SHUTDOWN);
        // If we switched into shutdown state, make SURE any remaining
        // items are emitted:
        emitOneBatch(drainAll(records));
    }

    @SuppressWarnings("empty-statement")
    public LinkedList<OpRecord<Ops>> drainAll(LinkedList<OpRecord<Ops>> records) {
        while (drain(records));
        if (!records.isEmpty()) {
            emitOneBatch(records);
        }
        return records;
    }

    void emitOneBatch(LinkedList<? extends OpRecord<Ops>> into) {
        try {
            eachDelegate(del -> {
                for (OpRecord<Ops> r : into) {
                    r.accept(del);
                }
            });
        } finally {
            into.clear();
        }
    }

    private void start() {
        emitter.start();
    }

    static class Cell<Ops extends Enum<Ops>> {

        private final OpRecord<Ops> record;
        private final Cell<Ops> prev;

        public Cell(OpRecord<Ops> record, Cell<Ops> prev) {
            this.record = record;
            this.prev = prev;
        }

        void addTo(LinkedList<? super OpRecord<Ops>> l) {
            // If we want fifo order we need addFirst
            l.addFirst(record);
            if (prev != null) {
                prev.addTo(l);
            }
        }
    }

    static abstract class OpRecord<Ops extends Enum<Ops>> implements Consumer<ProbeImplementation<? super Ops>> {

    }

    @Override
    public void onStartup() {
        push(new LifecycleRecord<>(true));
    }

    @Override
    public void onShutdown() {
        push(new LifecycleRecord<>(false));
    }

    @Override
    public void onSendResponse(Ops op, RoutingContext event, int status, Optional<?> payload) {
        push(new SendResponseRecord<>(op, event, status, payload));
    }

    private static final class SendResponseRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final int status;
        private final Optional<?> payload;

        public SendResponseRecord(Ops op, RoutingContext event, int status, Optional<?> payload) {
            this.op = op;
            this.event = event;
            this.status = status;
            this.payload = payload;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onSendResponse(op, event, status, payload);
        }
    }

    @Override
    public void onResponseCompleted(RoutingContext event, int status, Optional<?> payload) {
        push(new ResponseCompletedRecord<>(event, status, payload));
    }

    private static final class ResponseCompletedRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final RoutingContext event;
        private final int status;
        private final Optional<?> payload;

        public ResponseCompletedRecord(RoutingContext event, int status, Optional<?> payload) {
            this.event = event;
            this.status = status;
            this.payload = payload;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onResponseCompleted(event, status, payload);
        }
    }

    @Override
    public void onPayloadRead(Ops op, RoutingContext event,
            Class<? extends Handler<RoutingContext>> handler,
            Buffer buffer) {
        buffer = Buffer.buffer(buffer.getByteBuf().duplicate());
        push(new PayloadReadRecord<>(op, event, handler, buffer));
    }

    private static final class PayloadReadRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final Class<? extends Handler<RoutingContext>> handler;
        private final Buffer buffer;

        public PayloadReadRecord(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Buffer buffer) {
            this.op = op;
            this.event = event;
            this.handler = handler;
            this.buffer = buffer;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onPayloadRead(op, event, handler, buffer);
        }
    }

    @Override
    public void onNonOperationFailure(String message, Throwable thrown) {
        push(new NonOpFailureRecord<>(message, thrown));
    }

    private static final class NonOpFailureRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final String message;
        private final Throwable thrown;

        public NonOpFailureRecord(String message, Throwable thrown) {
            this.message = message;
            this.thrown = thrown;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onNonOperationFailure(message, thrown);
        }
    }

    @Override
    public void onMetric(Ops op, RoutingContext event, String name, Number value) {
        push(new MetricRecord<>(op, event, name, value));
    }

    private static final class MetricRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final String name;
        private final Number value;

        public MetricRecord(Ops op, RoutingContext event, String name, Number value) {
            this.op = op;
            this.event = event;
            this.name = name;
            this.value = value;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onMetric(op, event, name, value);
        }
    }

    @Override
    public void onFailure(Ops op, RoutingContext event, Throwable thrown) {
        push(new FailureRecord<>(op, event, thrown));
    }

    private static final class FailureRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final Throwable thrown;

        public FailureRecord(Ops op, RoutingContext event, Throwable thrown) {
            this.op = op;
            this.event = event;
            this.thrown = thrown;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onFailure(op, event, thrown);
        }
    }

    @Override
    public void onEvent(Ops op, String event) {
        push(new EventRecord<>(op, event));
    }

    private static final class EventRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final String event;

        public EventRecord(Ops op, String event) {
            this.op = op;
            this.event = event;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onEvent(op, event);
        }
    }

    @Override
    public void onEnterHandler(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
        push(new EnterHandlerRecord<>(op, event, handler));
    }

    private static final class EnterHandlerRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final Class<? extends Handler<RoutingContext>> handler;

        public EnterHandlerRecord(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
            this.op = op;
            this.event = event;
            this.handler = handler;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onEnterHandler(op, event, handler);
        }
    }

    private static final class LifecycleRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final boolean isStartup;

        LifecycleRecord(boolean isStartup) {
            this.isStartup = isStartup;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            if (isStartup) {
                t.onStartup();
            } else {
                t.onShutdown();
            }
        }
    }

    static enum State {
        NOT_STARTED,
        STARTED,
        SHUTDOWN;
    }
}
