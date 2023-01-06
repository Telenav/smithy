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
package com.telenav.smithy.vertx.adapter;

import com.telenav.smithy.http.ResponseException;
import com.telenav.smithy.http.AuthenticationResultConsumer;
import com.telenav.smithy.http.AuthenticationResultConsumerFactory;
import com.mastfrog.util.service.ServiceProvider;
import com.mastfrog.util.strings.Strings;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(AuthenticationResultConsumerFactory.class)
public class VertxAuthenticationResultConsumerFactory extends AuthenticationResultConsumerFactory {

    @Override
    protected <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut, boolean optional) {
        return new ARC<>(fut, optional);
    }

    static class ARC<T> implements AuthenticationResultConsumer<T> {

        private final CompletableFuture<T> fut;
        private final boolean optional;

        public ARC(CompletableFuture<T> fut, boolean optional) {
            this.fut = fut;
            this.optional = optional;
        }

        @Override
        public void ok(T obj) {
            fut.complete(obj);
        }

        @Override
        public void unauthorized() {
            failed(new ResponseException(401, "Unauthorized"));
        }

        @Override
        public void unauthorized(String headerName, String headerValue) {
            failed(new ResponseException(401, "Unauthorized", headerName, headerValue));
        }

        @Override
        public void forbidden() {
            failed(new ResponseException(403, "Forbidden"));
        }

        @Override
        public void failed(Throwable thrown) {
            if (thrown instanceof ResponseException) {
                throw (ResponseException) thrown;
            }
            fut.completeExceptionally(new ResponseException(500, Strings.toString(thrown), thrown));
        }

    }

}
