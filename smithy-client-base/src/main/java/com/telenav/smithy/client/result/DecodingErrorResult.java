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
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.Strings;

import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
final class DecodingErrorResult<T> implements ServiceResult<T> {

    private final String body;
    private final Throwable err;

    DecodingErrorResult(String body, Throwable err) {
        this.err = notNull("err", err);
        this.body = body;
    }
    
    public String toString() {
        return reason().name().toLowerCase()
                + "\n" + body
                + "\n\n" + Strings.toString(err);
    }

    @Override
    public Optional<String> errorMessage() {
        return Optional.ofNullable(body);
    }

    @Override
    public Optional<Throwable> thrown() {
        return Optional.of(err);
    }

    @Override
    public CompletionReason reason() {
        return CompletionReason.INVALID_RESPONSE;
    }

}
