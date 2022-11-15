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
