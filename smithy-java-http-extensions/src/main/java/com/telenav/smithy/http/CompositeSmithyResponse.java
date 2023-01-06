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
package com.telenav.smithy.http;

import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Tim Boudreau
 */
final class CompositeSmithyResponse<T> implements SmithyResponse<T> {

    private final CompletableFuture<T> future;
    private final SmithyResponseHead head;

    CompositeSmithyResponse(CompletableFuture<T> future, SmithyResponseHead head) {
        this.future = future;
        this.head = head;
    }

    private void checkCompleted() {
        if (future.isDone()) {
            throw new IllegalStateException(
                    "Cannot add headers or set response code after response has been sent");
        }
    }

    @Override
    public <R> CompositeSmithyResponse<T> add(HeaderSpec<R> header, R value) {
        checkCompleted();
        head.add(header, value);
        return this;
    }

    @Override
    public SmithyResponse status(int code) {
        checkCompleted();
        head.status(code);
        return this;
    }

    @Override
    public void complete(T responseObject) {
        future.complete(responseObject);
    }

    @Override
    public void completeExceptionally(Throwable thrown) {
        future.completeExceptionally(thrown);
    }
}
