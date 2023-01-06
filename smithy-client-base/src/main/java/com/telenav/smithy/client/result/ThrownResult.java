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
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
class ThrownResult<T> implements ServiceResult<T> {

    private final Throwable thrown;
    private final CompletionReason reason;

    public ThrownResult(Throwable thrown) {
        this(thrown, CompletionReason.ERRORED);
    }

    public ThrownResult(Throwable thrown, CompletionReason reason) {
        this.thrown = notNull("thrown", thrown);
        this.reason = notNull("reason", reason);
    }

    @Override
    public Optional<Throwable> thrown() {
        return Optional.of(thrown);
    }

    @Override
    public String toString() {
        return reason().name().toLowerCase() + ' ' + thrown.toString();
    }

    @Override
    public CompletionReason reason() {
        return reason;
    }

}
