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
package com.telenav.smithy.client.result;

import com.telenav.smithy.client.state.CompletionReason;
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
        return CancelledResult.cancelled();
    }

}
