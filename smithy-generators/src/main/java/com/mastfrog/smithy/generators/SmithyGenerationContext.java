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
package com.mastfrog.smithy.generators;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Context within which smithy generation runs, aggregating the settings and
 * destinations for the current run, and providing a place to store information
 * needed to perform generation.
 *
 * @author Tim Boudreau
 */
public final class SmithyGenerationContext {

    private static final ThreadLocal<SmithyGenerationContext> CTX
            = new ThreadLocal<>();
    private final Map<SettingsKey<?>, Object> keySettings
            = new ConcurrentHashMap<>();

    private final SmithyDestinations dests;
    private final SmithyGenerationSettings settings;

    SmithyGenerationContext(SmithyDestinations dests,
            SmithyGenerationSettings settings) {
        this.dests = dests;
        this.settings = settings;
    }

    /**
     * Add a key to the generation context, to allow plugged-in components to
     * share data with each other if both have access to the key.
     *
     * @param <T> The value type
     * @param key A key
     * @param val A value
     * @return this
     */
    public <T> SmithyGenerationContext put(SettingsKey<T> key, T val) {
        keySettings.put(notNull("key", key), notNull("val", val));
        return this;
    }

    /**
     * Add a key to the generation context, to allow plugged-in components to
     * share data with each other if both have access to the key, adding it
     * before the passed runnable is run, and removing it or restoring the
     * previous value afterwards.
     *
     * @param <T> The value type
     * @param key The key
     * @param val The value
     * @param run A runnable to run with this key in the context
     */
    public <T> void with(SettingsKey<T> key, T val, Runnable run) {
        Object old = keySettings.get(notNull("key", key));
        try {
            keySettings.put(key, notNull("val", val));
            run.run();
        } finally {
            if (old == null) {
                keySettings.remove(key);
            } else {
                keySettings.put(key, old);
            }
        }
    }

    /**
     * Add a key to the generation context, to allow plugged-in components to
     * share data with each other if both have access to the key, adding it
     * before the passed runnable is run, and removing it or restoring the
     * previous value afterwards.
     *
     * @param <T> The key type
     * @param <R> The return type of the passed supplier
     * @param key A key
     * @param val The value to relate to the key
     * @param run A supplier which computes something
     * @return The result of running the supplier
     */
    public <T, R> R with(SettingsKey<T> key, T val, Supplier<R> run) {
        Object old = keySettings.get(notNull("key", key));
        try {
            keySettings.put(key, notNull("val", val));
            return run.get();
        } finally {
            if (old == null) {
                keySettings.remove(key);
            } else {
                keySettings.put(key, old);
            }
        }
    }

    /**
     * Get the instance associated with a key in this context, failing over to
     * any value returned by the generation settings if none is present.
     *
     * @param <T> A key
     * @param key A value
     * @return A value if there is one
     */
    public <T> Optional<T> get(SettingsKey<T> key) {
        Object o = keySettings.get(notNull("key", key));
        if (o == null) {
            return settings.get(key);
        }
        return Optional.of(key.cast(o));
    }

    public <T> T computeIfAbsent(SettingsKey<T> key, Supplier<T> fallback) {
        Object o = keySettings.computeIfAbsent(notNull("key", key),
                k -> fallback.get());
        return key.cast(o);
    }

    public SmithyDestinations destinations() {
        return dests;
    }

    public SmithyGenerationSettings settings() {
        return settings;
    }

    public static SmithyGenerationContext get() {
        SmithyGenerationContext result = CTX.get();
        if (result == null) {
            throw new IllegalStateException("Not in context");
        }
        return result;
    }

    public void run(ThrowingRunnable run) throws Exception {
        SmithyGenerationContext old = CTX.get();
        CTX.set(this);
        try {
            run.run();
        } finally {
            CTX.set(old);
        }
    }

    public <T> T run(ThrowingSupplier<T> run) throws Exception {
        SmithyGenerationContext old = CTX.get();
        CTX.set(this);
        try {
            return run.get();
        } finally {
            CTX.set(old);
        }
    }

    public static boolean isInContext() {
        return CTX.get() != null;
    }

}
