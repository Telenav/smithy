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
package com.telenav.periodic.metrics;

import com.mastfrog.shutdown.hooks.ShutdownHooks;
import static com.telenav.periodic.metrics.IntervalSpec.durationOf;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import static java.time.ZonedDateTime.now;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_DAY;
import static java.time.temporal.ChronoField.MILLI_OF_SECOND;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;
import java.time.temporal.ChronoUnit;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import java.util.List;
import java.util.Optional;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Implementation of Delayed which pegs its deadline to round numbers - every 5
 * minutes will be 5 minutes after every hour, every hour means at minute 0 of
 * every hour, etc. This means that when one is initially created, it may not be
 * started yet - that is, if it will count more time than the amount it is
 * supposed to, it will report itself as not started until it is it's interval
 * before the next round-number deadline.
 * <p>
 * The implementation parameterizes on a type and can carry a data payload.
 * </p>
 */
final class ClockInterval<T> implements Delayed, Supplier<T> {

    private final AtomicReference<ZonedDateTime> deadline = new AtomicReference<>();
    private final IntervalSpec interval;
    private final T data;
    private boolean started;

    ClockInterval(int amount, ChronoUnit u, T data) {
        this(amount, u, now(), data);
    }

    ClockInterval(int amount, ChronoUnit u, ZonedDateTime now, T data) {
        this(new IntervalSpec(u, amount), now, data);
    }

    ClockInterval(IntervalSpec interval, T data) {
        this(interval, now(), data);
    }

    ClockInterval(IntervalSpec interval, ZonedDateTime now, T data) {
        this.interval = interval;
        this.data = data;
        ChronoField field = fieldForUnit(interval.unit());
        int amount = interval.amount();
        ZonedDateTime dl = interval.unit().addTo(now, amount);
        // Zero out the seconds for minutes, minutes and seconds for hours, and so forth
        for (ChronoField p : preceding(field)) {
            dl = dl.with(p, p.range().getLargestMinimum());
        }
        // If we are in an interval of more than one unit, align to the next multiple
        if (amount > 1 && dl.get(field) % amount != 0) {
            long nearest = (dl.get(field) / amount) * amount;
            dl = dl.with(field, nearest);
        }
        deadline.set(dl);
        started = isStarted();
    }

    private boolean started() {
        return started;
    }

    public IntervalSpec spec() {
        return this.interval;
    }

    @Override
    public T get() {
        return data;
    }

    public Duration remaining() {
        return Duration.ofMillis(delay(false));
    }

    public Duration period() {
        return interval.duration();
    }

    public boolean isStarted() {
        Duration pending = Duration.between(Instant.now(), deadline());
        return pending.isNegative() || pending.toMillis() < durationOf(interval.unit()).toMillis();
    }

    @Override
    public String toString() {
        return "Every " + interval.amount() + " " + interval.unit().name() + " - next "
                + deadline().format(DateTimeFormatter.RFC_1123_DATE_TIME) + " started " + isStarted();
    }

    private ZonedDateTime withNextOf(ZonedDateTime inst, ChronoField f, int by) {
        ZonedDateTime now = inst.plus(Duration.ofMillis(1));
        ChronoUnit unit = interval.unit();
        int amount = interval.amount();
        ChronoField field = fieldForUnit(unit);
        ZonedDateTime result = unit.addTo(now, amount);
        // Same logic as in the constructor
        for (ChronoField p : preceding(field)) {
            result = result.with(p, p.range().getLargestMinimum());
        }
        if (amount > 1 && result.get(field) % amount != 0) {
            long nearest = (result.get(field) / amount) * amount;
            result = result.with(field, nearest);
        }
        return result;
    }

    public void resetDeadline() {
        // This is not called from getDelay() if it is 0 or negative, as
        // that may be called multiple times by the queue when doing comparisions.
        // The queue will call reset after the item has been removed from the queue,
        // before it is returned
        ZonedDateTime next = withNextOf(deadline.get(), fieldForUnit(interval.unit()), interval.amount());
        started = true;
        deadline.set(next);
    }

    public ZonedDateTime deadline() {
        return deadline.get();
    }

    private static Optional<ChronoField> following(ChronoField f) {
        switch (f) {
            case YEAR:
                return empty();
            case DAY_OF_YEAR:
                return of(YEAR);
            case HOUR_OF_DAY:
                return of(DAY_OF_YEAR);
            case MINUTE_OF_HOUR:
                return of(HOUR_OF_DAY);
            case SECOND_OF_MINUTE:
                return of(MINUTE_OF_HOUR);
            case MILLI_OF_SECOND:
                return of(SECOND_OF_MINUTE);
            default:
                throw new AssertionError(f);
        }
    }

    private static List<ChronoField> preceding(ChronoField f) {
        switch (f) {
            case YEAR:
                return asList(DAY_OF_YEAR, HOUR_OF_DAY, MINUTE_OF_HOUR, SECOND_OF_MINUTE, MILLI_OF_SECOND);
            case DAY_OF_YEAR:
                return asList(HOUR_OF_DAY, MINUTE_OF_HOUR, SECOND_OF_MINUTE, MILLI_OF_SECOND);
            case HOUR_OF_DAY:
                return asList(MINUTE_OF_HOUR, SECOND_OF_MINUTE, MILLI_OF_SECOND);
            case MINUTE_OF_HOUR:
                return asList(SECOND_OF_MINUTE, MILLI_OF_SECOND);
            case SECOND_OF_MINUTE:
                return asList(MILLI_OF_DAY);
            case MILLI_OF_SECOND:
                return emptyList();
            default:
                throw new AssertionError(f);
        }
    }

    private static ChronoField fieldForUnit(ChronoUnit unit) {
        switch (unit) {
            case YEARS:
                return YEAR;
            case DAYS:
                return DAY_OF_YEAR;
            case HOURS:
                return HOUR_OF_DAY;
            case MINUTES:
                return MINUTE_OF_HOUR;
            case SECONDS:
                return SECOND_OF_MINUTE;
            case MILLIS:
                return MILLI_OF_SECOND;
            default:
                throw new AssertionError("Unsupported conversion: " + unit);
        }
    }

    /**
     * Implementation of Delayed - PeriodicQueue uses a DelayQueue to emit
     * metrics as they hit their deadline.
     *
     * @param unit A time unit
     * @return The time remaining, or a negative number if expired
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delay(false), TimeUnit.MILLISECONDS);
    }

    private long delay(boolean recompute) {
        ZonedDateTime dl = deadline();
        Instant now = Instant.now();
        long distMillis = dl.toInstant().toEpochMilli() - now.toEpochMilli();
        if (distMillis < 0 && recompute) {
            resetDeadline();
        }
        return distMillis;
    }

    @Override
    public int compareTo(Delayed o) {
        long del = o.getDelay(TimeUnit.MILLISECONDS);
        long od = delay(false);
        return Long.compare(od, del);
    }

    /**
     * A simple single threaded delay queue which can hold a bunch of
     * PeriodicPeriods of different periods, and call a consumer with the
     * payload of each one as it expires, and then reset its deadline and
     * reenqueue it - so we can have a bunch of periodic metrics writers
     * (1-minute, 5-minute, etc.) and they all get run at their time without
     * much overhead.
     *
     * @param <T> The payload type for the queue members
     */
    public static class PeriodicQueue<T> {

        private final DelayQueue<ClockInterval<T>> q = new DelayQueue<>();
        private final BiConsumer<ClockInterval<T>, Boolean> onExpiry;
        private final Thread thread = new Thread(this::loop, "periodic-queue");
        private final Runnable onShutdown = this::onShutdown; // must remain strongly referenced, can't be a lambda or reference
        private volatile boolean isShuttingDown;

        PeriodicQueue(ShutdownHooks hooks, Iterable<? extends ClockInterval<T>> q, BiConsumer<ClockInterval<T>, Boolean> onExpiry) {
            this.onExpiry = onExpiry;
            hooks.addFirst(onShutdown);
            q.forEach(this.q::offer);
        }

        private void onShutdown() {
            isShuttingDown = true;
            if (thread.isAlive()) {
                // Interrupt the thread to exit
                thread.interrupt();
            }
        }

        public synchronized PeriodicQueue<T> start() {
            if (!thread.isAlive()) {
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                thread.start();
            }
            return this;
        }

        private void loop() {
            while (!isShuttingDown) {
                try {
                    ClockInterval<T> per = q.take();
                    boolean wasStarted = per.started();
                    try {
                        onExpiry.accept(per, wasStarted);
                    } catch (Exception | Error e) {
                        e.printStackTrace(System.err);
                    } finally {
                        per.resetDeadline();
                        q.offer(per);
                    }
                } catch (InterruptedException ex) {
                    // do nothing - possibly spurious interrupt - if we are really
                    // shutting down, isShuttingDown will be true and we will exit.
                }
            }
            q.clear();
        }
    }

}
