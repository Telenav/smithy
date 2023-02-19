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

import io.netty.buffer.ByteBuf;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.util.Collections;
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
        super(Collections.singletonList(delegate));
        emitter.setPriority(Thread.NORM_PRIORITY - 1);
        emitter.setDaemon(true);
    }

    boolean drain(LinkedList<? super OpRecord<Ops>> into) {
        // Drain the contents of the trieber stack, removing its head and
        // collecting its contents into the passed list.
        Cell<Ops> cell = head.getAndSet(null);
        if (cell != null) {
            cell.addTo(into);
            return true;
        }
        return false;
    }

    void push(OpRecord<Ops> op) {
        // Push a new cell atomically onto the trieber stack
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
        System.err.println("emit loop exit");
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

    /**
     * A simple trieber stack.
     *
     * @param <Ops> The operation type
     */
    private static final class Cell<Ops extends Enum<Ops>> {

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
    public void onLaunched(Verticle verticle, String msg) {
        push(new LaunchRecord<>(verticle, msg));
    }

    static final class LaunchRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Verticle verticle;
        private final String msg;

        public LaunchRecord(Verticle verticle, String msg) {
            this.verticle = verticle;
            this.msg = msg;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onLaunched(verticle, msg);
        }
    }

    @Override
    public void onLaunchFailure(Verticle verticle, DeploymentOptions opts, Throwable thrown) {
        push(new LaunchFailureRecord<>(verticle, opts, thrown));
    }

    static final class LaunchFailureRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Verticle verticle;
        private final DeploymentOptions opts;
        private final Throwable thrown;

        public LaunchFailureRecord(Verticle verticle, DeploymentOptions opts, Throwable thrown) {
            this.verticle = verticle;
            this.opts = opts;
            this.thrown = thrown;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onLaunchFailure(verticle, opts, thrown);
        }
    }

    @Override
    public void onStartRequest(Ops op, RoutingContext event) {
        push(new StartRequestRecord<>(op, event));
    }

    private static final class StartRequestRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;

        public StartRequestRecord(Ops op, RoutingContext event) {
            this.op = op;
            this.event = event;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onStartRequest(op, event);
        }
    }

    @Override
    public void onBeforeSendResponse(Ops op, RoutingContext event, Optional<?> payload) {
        push(new SendResponseRecord<>(op, event, payload));
    }

    private static final class SendResponseRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final Optional<?> payload;

        public SendResponseRecord(Ops op, RoutingContext event, Optional<?> payload) {
            this.op = op;
            this.event = event;
            this.payload = payload;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onBeforeSendResponse(op, event, payload);
        }
    }

    @Override
    public void onAfterSendResponse(Ops op, RoutingContext event, int status) {
        push(new AfterSendResponseRecord<>(op, event, status));
    }

    private static final class AfterSendResponseRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final int status;

        public AfterSendResponseRecord(Ops op, RoutingContext event, int status) {
            this.op = op;
            this.event = event;
            this.status = status;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onAfterSendResponse(op, event, status);
        }
    }

    @Override
    public void onResponseCompleted(Ops op, RoutingContext event, int status) {
        push(new ResponseCompletedRecord<>(op, event, status));
    }

    private static final class ResponseCompletedRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final RoutingContext event;
        private final int status;
        private final Ops op;

        public ResponseCompletedRecord(Ops op, RoutingContext event, int status) {
            this.event = event;
            this.status = status;
            this.op = op;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onResponseCompleted(op, event, status);
        }
    }

    @Override
    public void onBeforePayloadRead(Ops op, RoutingContext event,
            Class<? extends Handler<RoutingContext>> handler,
            Buffer buffer) {
        if (buffer == null) {
            System.err.println("before payload read, but no buffer.");
            return;
        }
        ByteBuf bb = buffer.getByteBuf();
        if (bb == null) {
            return;
        }
        buffer = Buffer.buffer(bb.duplicate());
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
            t.onBeforePayloadRead(op, event, handler, buffer);
        }
    }

    @Override
    public void onAfterPayloadRead(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Optional<?> payload) {
        push(new AfterPayloadReadRecord<>(op, event, handler, payload));
    }

    private static final class AfterPayloadReadRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final RoutingContext event;
        private final Class<? extends Handler<RoutingContext>> handler;
        private final Optional<?> buffer;

        public AfterPayloadReadRecord(Ops op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler, Optional<?> buffer) {
            this.op = op;
            this.event = event;
            this.handler = handler;
            this.buffer = buffer;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onAfterPayloadRead(op, event, handler, buffer);
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
    public void onEvent(Ops op, String event, Object payload) {
        push(new EventRecord<>(op, event, payload));
    }

    private static final class EventRecord<Ops extends Enum<Ops>> extends OpRecord<Ops> {

        private final Ops op;
        private final String event;
        private final Object payload;

        public EventRecord(Ops op, String event, Object payload) {
            this.op = op;
            this.event = event;
            this.payload = payload;
        }

        @Override
        public void accept(ProbeImplementation<? super Ops> t) {
            t.onEvent(op, event, payload);
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
