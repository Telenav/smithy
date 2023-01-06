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
