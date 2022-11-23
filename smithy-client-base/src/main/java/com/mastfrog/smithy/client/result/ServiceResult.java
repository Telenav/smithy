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
package com.mastfrog.smithy.client.result;

import com.mastfrog.smithy.client.state.CompletionReason;
import com.mastfrog.util.preconditions.Exceptions;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public interface ServiceResult<T> {

    CompletionReason reason();

    default void rethrow() {
        Optional<Throwable> val = thrown();
        if (val.isPresent()) {
            Exceptions.chuck(val.get());
        }
    }

    default boolean isTimedOut() {
        return false;
    }

    /**
     * Get the raw HTTP response info returned by the http client.
     *
     * @return Response info, if any (in case of a thrown exception during
     * processing the request, or cancellation, there may not be).
     */
    default Optional<HttpResponse.ResponseInfo> info() {
        return Optional.empty();
    }

    /**
     * Get the response info if a response was received.
     *
     * @return the status code, or 0 if no status was received (e.g. the request
     * was cancelled or timed out)
     */
    default int status() {
        Optional<HttpResponse.ResponseInfo> info = info();
        if (info.isPresent()) {
            return info.get().statusCode();
        }
        return 0;
    }

    default Optional<String> header(CharSequence headerName) {
        return info().flatMap(info -> {
            return info.headers().firstValue(headerName.toString());
        });
    }

    default <T> Optional<T> header(CharSequence headerName,
            Function<? super CharSequence, T> converter) {
        return header(headerName).map(converter);
    }

    default Optional<T> result() {
        return Optional.empty();
    }

    default Optional<String> errorMessage() {
        Optional<Throwable> th = thrown();
        return th.map(Throwable::toString).or(Optional::empty);
    }

    default Optional<Throwable> thrown() {
        return Optional.empty();
    }

    default boolean isComplete() {
        return result().isPresent() || errorMessage().isPresent();
    }

    default boolean isError() {
        return errorMessage().isPresent();
    }

    default boolean isSuccess() {
        Optional<HttpResponse.ResponseInfo> info = info();
        return info.map(ifo -> ifo.statusCode() < 400).orElse(false);
    }

    static <T> ServiceResult<T> thrown(Throwable thrown) {
        return new ThrownResult<>(thrown);
    }

    static <T> ServiceResult<T> decodingError(String body, Throwable thrown) {
        return new DecodingErrorResult<>(body, thrown);
    }

    static <T> ServiceResult<T> failed(HttpResponse.ResponseInfo info, String body) {
        return new FailureResponseResult<>(body, info);
    }

    static <T> ServiceResult<T> success(HttpResponse.ResponseInfo info, T body) {
        return new SuccessResult<>(body, info);
    }

    static <T> ServiceResult<T> timeout() {
        return TimedoutResult.timedoutResult();
    }

    static <T> ServiceResult<T> cancelled() {
        return new CancelledResult<>();
    }

}
