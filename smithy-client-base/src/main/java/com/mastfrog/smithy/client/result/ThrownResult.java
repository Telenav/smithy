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
