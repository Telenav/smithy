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

import java.net.http.HttpResponse;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class SuccessResult<T> implements ServiceResult<T> {

    private final T object;
    private final HttpResponse.ResponseInfo info;

    public SuccessResult(T object, HttpResponse.ResponseInfo info) {
        this.object = object;
        this.info = info;
    }

    @Override
    public CompletionReason reason() {
        return CompletionReason.COMPLETED;
    }

    @Override
    public Optional<HttpResponse.ResponseInfo> info() {
        return Optional.ofNullable(info);
    }

    @Override
    public Optional<T> result() {
        return Optional.ofNullable(object);
    }

    @Override
    public String toString() {
        return reason().name().toLowerCase() + " " + info.statusCode() + ": " + object;
    }

}
