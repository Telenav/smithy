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
package com.telenav.smithy.vertx.adapter;

import com.telenav.smithy.http.HeaderSpec;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.smithy.http.SmithyResponse.SmithyResponseHead;
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
