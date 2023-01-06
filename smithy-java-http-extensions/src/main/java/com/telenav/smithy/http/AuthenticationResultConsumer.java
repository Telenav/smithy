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
