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
package com.mastfrog.smithy.http;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import static com.mastfrog.smithy.http.HeaderTypes.headerTypes;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Async (or sync) response object which can be completed with the result of an
 * http request.
 * <p>
 * Implements <code>BiConsumer&lt;T,Throwable&gt;</code> for ease of use with
 * async frameworks, for which this is a common handler pattern.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface SmithyResponse<T> extends BiConsumer<T, Throwable> {

    /**
     * Completes the response either exceptionally with the passed throwable
     * if it is not null, or with the first argument (even if it is null,
     * which it may be for an empty response).  Useful with CompletableFuture,
     * as well as future handlers for vert.x and others.
     *
     * @param t The response object
     * @param u A throwable
     */
    @Override
    public default void accept(T t, Throwable u) {
        if (u != null) {
            completeExceptionally(u);
        } else {
            complete(t);
        }
    }

    /**
     * Create a SmithyResponse, wrapping the passed response head and
     * completable future into a response.
     *
     * @param <T> The type
     * @param head The head
     * @param future The future which will restart response processing and send
     * the response
     * @return A new SmithyResponse
     */
    public static <T> SmithyResponse<T> smithyResponse(SmithyResponseHead head, CompletableFuture<T> future) {
        return new CompositeSmithyResponse<>(future, head);
    }

    /**
     * Add a response header.
     *
     * @param <R> The object type
     * @param header The header (use HeaderTypes.get() for type-safe standard
     * headers or to create custom ones)
     * @param value The header value, not null
     * @return this
     * @throws IllegalStateException if called after complete() or
     * completeExceptionally() has been called
     */
    <R> SmithyResponse<T> add(HeaderSpec<R> header, R value);

    /**
     * Add a response header.
     *
     * @param <R> The object type
     * @param header The header (use HeaderTypes.get() for type-safe standard
     * headers or to create custom ones)
     * @param value The header value, not null
     * @return this
     * @throws IllegalStateException if called after complete() or
     * completeExceptionally() has been called
     */
    default SmithyResponse<T> add(CharSequence headerName, String headerValue) {
        return add(headerTypes().stringHeader(headerName), headerValue);
    }

    /**
     * Run some code if and only if the passed supplier returns null; if it
     * returns non-null, complete this response exceptionally with the
     * exception.
     *
     * @param fallible A function (such as on VertX's async futures) which can
     * return a throwable if an asychrnous operation failed.
     * @param ifOk Some code to run on success
     * @return whether the runnable was called
     */
    default boolean ifNotFailed(Supplier<Throwable> fallible, ThrowingRunnable ifOk) {
        Throwable thrown = fallible.get();
        if (thrown != null) {
            completeExceptionally(thrown);
            return false;
        }
        ifOk.toNonThrowing().run();
        return true;
    }

    /**
     * Create a BiConsumer that takes an instance of some type and a throwable -
     * this is a pattern common in many async frameworks for handlers of async
     * operations. If there is a throwable, this request will be completed
     * exceptionally; otherwise the passed consumer will be called with the
     * result argument.
     * <p>
     * This provides the error-handling side of chaining async operations while
     * ensuring that the request is always completed in the event of failure.
     * </p>
     *
     * @param <S> The result type
     * @param onSuccess A
     * @return
     */
    default <S> BiConsumer<S, Throwable> callback(ThrowingConsumer<S> onSuccess) {
        return (obj, thrown) -> {
            if (thrown != null) {
                completeExceptionally(thrown);
            } else {
                onSuccess.toNonThrowing().accept(obj);
            }
        };
    }

    /**
     * Set the status code for the response.
     *
     * @param code The status code
     * @return this
     * @throws IllegalStateException if called after complete() or
     * completeExceptionally() has been called
     */
    SmithyResponse<T> status(int code);

    /**
     * Complete the response.
     *
     * @param responseObject The response body
     */
    void complete(T responseObject);

    /**
     * Complete the response exceptionally.
     *
     * @param thrown The thrown exception
     */
    void completeExceptionally(Throwable thrown);

    /**
     * Some implementations may use this mechanism to provide raw-access to the
     * underlying framework's request object.
     *
     * @param <T> A type
     * @param type A type
     * @return An optional containing the type if one can be prodced
     */
    default <T> Optional<T> unwrap(Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    /**
     * For implementors, the response head sans the body methods which will be
     * handled by a CompletableFuture.
     *
     */
    public interface SmithyResponseHead {

        /**
         * Implement to attach a header to a response. The header object can be
         * used to convert the value to a string if needed.
         *
         * @param <T> The type
         * @param header The header name / converter
         * @param value The header value, not null
         */
        <T> void add(HeaderSpec<T> header, T value);

        /**
         * Implement to set the response status code.
         *
         * @param code A response code
         */
        void status(int code);

    }
}
