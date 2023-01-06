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
import com.mastfrog.util.preconditions.Checks;

import java.net.http.HttpResponse;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
class FailureResponseResult<T> implements ServiceResult<T> {

    private final String errorMessage;
    private final HttpResponse.ResponseInfo info;

    public FailureResponseResult(String errorMessage, HttpResponse.ResponseInfo info) {
        this.errorMessage = Checks.notNull("errorMessage", errorMessage);
        this.info = Checks.notNull("info", info);
    }

    @Override
    public CompletionReason reason() {
        return CompletionReason.FAILED;
    }

    @Override
    public Optional<HttpResponse.ResponseInfo> info() {
        return Optional.of(info);
    }

    @Override
    public Optional<String> errorMessage() {
        return Optional.of(errorMessage);
    }

    @Override
    public String toString() {
        return reason().name().toLowerCase() + ' '
                + info.statusCode() + ": " + errorMessage;
    }

}
