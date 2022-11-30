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

import com.mastfrog.smithy.http.HeaderSpec;
import com.mastfrog.smithy.http.SmithyResponse;
import com.mastfrog.smithy.http.SmithyResponse.SmithyResponseHead;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Tim Boudreau
 */
public class VertxResponseCompletableFutureAdapter<T> {

    public static <T> SmithyResponse<T> smithyResponse(RoutingContext ctx, CompletableFuture<T> fut) {
        return SmithyResponse.smithyResponse(new Head(ctx.response()), fut);
    }

    static class Head implements SmithyResponseHead {

        private final HttpServerResponse response;

        public Head(HttpServerResponse response) {
            this.response = response;
        }

        @Override
        public <T> void add(HeaderSpec<T> header, T value) {
            response.putHeader(header.name(), header.toCharSequence(value));
        }

        @Override
        public void status(int code) {
            response.setStatusCode(code);
        }

    }
}