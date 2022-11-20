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

import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.function.state.Int;
import com.mastfrog.function.state.Lng;
import static java.lang.Math.min;
import static java.lang.Thread.yield;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.IntUnaryOperator;

/**
 *
 * @author Tim Boudreau
 */
class DebugBodyPublisher implements HttpRequest.BodyPublisher {

    private final byte[] bytes;
    private final ExecutorService executor;

    public DebugBodyPublisher(byte[] bytes, ExecutorService executor) {
        this.bytes = bytes;
        this.executor = executor;
    }

    @Override
    public long contentLength() {
        System.out.println("DBP CLEN " + bytes.length);
        return bytes.length;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        System.out.println("DBP Subscribe");
        new PublishByteArray().subscribe(subscriber);
    }

    class PublishByteArray implements Flow.Publisher<ByteBuffer> {

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Sub(subscriber));
        }

        class Sub implements Flow.Subscription {

            private final Flow.Subscriber<? super ByteBuffer> subscriber;
            // left is read position, right is write position
            private final AtomicIntegerPair readWritePositions = new AtomicIntegerPair();
            private boolean completedSent;
            private volatile boolean cancelled;
            private final AtomicBoolean pumpInProgress = new AtomicBoolean();

            public Sub(Flow.Subscriber<? super ByteBuffer> subscriber) {
                this.subscriber = subscriber;
            }

            @Override
            public void request(long n) {
                if (cancelled || n == 0) {
                    return;
                }
                if (n < 0) {
                    throw new IllegalArgumentException("Negative request: " + n);
                }
                if (n > Integer.MAX_VALUE) {
                    n = Integer.MAX_VALUE;
                }
                int val = (int) n;
                readWritePositions.update(new IntegerPairUpdater() {
                    @Override
                    public void update(int left, int right, IntBiConsumer update) {
                        update.accept(left + val, right);
                    }
                });
                int oldPending = readWritePositions.addLeft(val);
                int newPending = oldPending + val;
                System.out.println("Request " + n + " old " + oldPending + " new " + newPending);

                if (true) {
                    if (!completedSent) {
                        completedSent = true;
                        executor.submit(() -> {
                            subscriber.onNext(ByteBuffer.wrap(bytes));
                            subscriber.onComplete();
                        });
                    }
                    return;
                }

                if (true || newPending >= bytes.length - readWritePositions.right() || newPending % 2 == 0) {

                    for (int i = 0; i < 5; i++) {
                        if (!pumpInProgress.get() && !cancelled) {
                            executor.submit(this::pump);
                            break;
                        } else {
                            yield();
                        }
                    }
                }

            }

            private int outstandingBytes() {
                return readWritePositions.read((requested, written) -> min(bytes.length, requested) - written);
            }

            private void pump() {
                if (pumpInProgress.compareAndSet(false, true)) {
                    try {
                        System.out.println("Pump " + Thread.currentThread().getName());
                        int outstanding;
                        while ((outstanding = outstandingBytes()) != 0) {
                            if (cancelled) {
                                return;
                            }
                            int pos = readWritePositions.addRight(outstanding);
                            if (pos >= bytes.length) {
                                break;
                            }
                            System.out.println("@ " + pos + " outstanding " + outstanding + " of " + bytes.length);
                            ByteBuffer buf = ByteBuffer.wrap(bytes, pos, outstanding);
                            subscriber.onNext(buf);
                        }
                        if (done()) {
                            synchronized (subscriber) {
                                if (!completedSent) {
                                    completedSent = true;
                                    subscriber.onComplete();
                                }
                            }
                        }
                    } finally {
                        pumpInProgress.set(false);
                    }
                }
            }

            private boolean done() {
                return readWritePositions.right() == bytes.length;
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        }
    }

    // borrowed from mastfrog-concurrent so as not to create a dependency
    static final class AtomicIntegerPair {

        private volatile long value;

        private static final AtomicLongFieldUpdater<AtomicIntegerPair> UPD
                = AtomicLongFieldUpdater.newUpdater(AtomicIntegerPair.class, "value");

        AtomicIntegerPair() {

        }

        AtomicIntegerPair(int a, int b) {
            this.value = pack(a, b);
        }

        AtomicIntegerPair(long value) {
            this.value = value;
        }

        public long toLong() {
            return value();
        }

        private long value() {
            return UPD.get(this);
        }

        public void fetch(IntBiConsumer pair) {
            long val = value();
            pair.accept(unpackLeft(val), unpackRight(val));
        }

        public int[] get() {
            int[] result = new int[2];
            fetch((a, b) -> {
                result[0] = a;
                result[1] = b;
            });
            return result;
        }

        public int addLeft(int amt) {
            return updateLeft(l -> l + amt);
        }

        public int addRight(int amt) {
            return updateRight(r -> r + amt);
        }

        public int updateRight(IntUnaryOperator rightFunction) {
            Int oldRight = Int.create();
            update((left, right, c) -> {
                oldRight.set(right);
                c.accept(left, rightFunction.applyAsInt(right));
            });
            return oldRight.getAsInt();
        }

        public int updateLeft(IntUnaryOperator leftFunction) {
            Int oldLeft = Int.create();
            update((left, right, c) -> {
                oldLeft.set(left);
                c.accept(leftFunction.applyAsInt(left), right);
            });
            return oldLeft.getAsInt();
        }

        public void update(IntUnaryOperator leftFunction, IntUnaryOperator rightFunction) {
            UPD.updateAndGet(this, old -> {
                int left = leftFunction.applyAsInt(unpackLeft(old));
                int right = rightFunction.applyAsInt(unpackRight(old));
                return pack(left, right);
            });
        }

        public void update(IntegerPairUpdater updater) {
            Lng val = Lng.create();
            UPD.updateAndGet(this, old -> {
                val.set(old);
                updater.update(unpackLeft(old), unpackRight(old), (newLeft, newRight) -> {
                    val.set(pack(newLeft, newRight));
                });
                return val.getAsLong();
            });
        }

        public int read(IntegerPairToIntFunction f) {
            long val = UPD.get(this);
            return f.apply(unpackLeft(value), unpackRight(value));
        }

        public boolean compareAndSet(int expectedLeftValue, int expectedRightValue, int newLeftValue, int newRightValue) {
            long expect = pack(expectedLeftValue, expectedRightValue);
            long nue = pack(newLeftValue, newRightValue);
            return UPD.compareAndSet(this, expect, nue);
        }

        public void swap() {
            UPD.updateAndGet(this, old -> {
                int left = unpackLeft(old);
                int right = unpackRight(old);
                return pack(right, left);
            });
        }

        public void set(int left, int right) {
            UPD.set(this, pack(left, right));
        }

        public void setLeft(int newLeft) {
            UPD.updateAndGet(this, old -> {
                int left = newLeft;
                int right = unpackRight(old);
                return pack(left, right);
            });
        }

        public void setRight(int newRight) {
            UPD.updateAndGet(this, old -> {
                int left = unpackLeft(old);
                int right = newRight;
                return pack(left, right);
            });
        }

        public int left() {
            long val = value();
            return (int) (val >> 32);
        }

        public int right() {
            return unpackRight(value());
        }

        static long pack(int left, int right) {
            return (((long) left) << 32) | (right & 0xFFFF_FFFFL);
        }

        static int unpackLeft(long value) {
            return (int) ((value >>> 32) & 0xFFFF_FFFFL);
        }

        static int unpackRight(long value) {
            return (int) (value & 0xFFFF_FFFFL);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("(");
            fetch((a, b) -> {
                sb.append(a).append(", ").append(b);
            });
            return sb.append(')').toString();
        }

    }

    public interface IntegerPairUpdater {

        void update(int left, int right, IntBiConsumer update);
    }

    public interface IntegerPairToIntFunction {

        int apply(int left, int right);
    }

}
