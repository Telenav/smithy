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
    public SmithyResponse<T> status(int code) {
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
