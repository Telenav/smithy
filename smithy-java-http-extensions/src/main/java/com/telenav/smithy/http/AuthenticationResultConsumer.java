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
 * Callback interface for providing an authentication result of some type.
 *
 * @author Tim Boudreau
 */
public interface AuthenticationResultConsumer<T> {

    void ok(T obj);

    default void unauthorized() {
        failed(new ResponseException(401, "Unauthorized"));
    }

    /**
     * Mark the response as unauthenticated, providing a header and header value
     * to attach to the response (e.g. www-authenticate).
     *
     * @param headerName A header name
     * @param headerValue A header value
     */
    default void unauthorized(String headerName, String headerValue) {
        failed(new ResponseException(401, "Unauthorized", headerName, headerValue));
    }

    default void forbidden() {
        failed(new ResponseException(403, "Forbidden"));
    }

    void failed(Throwable thrown);

    default void ok() {
        ok(null);
    }

    public static <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut,
            boolean optional) {
        return AuthenticationResultConsumerFactory.newConsumer(fut, optional);
    }
}
