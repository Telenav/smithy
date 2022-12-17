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
package com.mastfrog.smithy.http;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/**
 * Factory for things that accept the result of performing authentication, which
 * should be registered on the classpath in service loader for the framework
 * code is being generated for.
 *
 * @author Tim Boudreau
 */
public abstract class AuthenticationResultConsumerFactory {

    private static AuthenticationResultConsumerFactory FACTORY;

    public static <T> AuthenticationResultConsumer<T> newConsumer(CompletableFuture<T> fut, boolean optional) {

        AuthenticationResultConsumerFactory result = FACTORY;
        if (result == null) {
            ServiceLoader<AuthenticationResultConsumerFactory> all = ServiceLoader.load(AuthenticationResultConsumerFactory.class);
            Iterator<AuthenticationResultConsumerFactory> it = all.iterator();
            if (it.hasNext()) {
                result = FACTORY = it.next();
            } else {
                result = FACTORY = new DefaultFactory();
            }
        }
        return result.create(fut, optional);
    }

    protected abstract <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut, boolean optional);

    protected static abstract class AbstractAuthenticationResultConsumer<T> implements AuthenticationResultConsumer<T> {

        private final CompletableFuture<T> fut;
        private final boolean optional;

        protected AbstractAuthenticationResultConsumer(CompletableFuture<T> fut, boolean optional) {
            this.fut = fut;
            this.optional = optional;
        }

        @Override
        public void ok(T obj) {
            if (!optional && obj == null) {
                failed(new IllegalStateException(
                        "Passed authentication payload is null, but authentication "
                        + "is not optional for this operation"));
            }
            fut.complete(obj);
        }

        @Override
        public void failed(Throwable thrown) {
            fut.completeExceptionally(thrown);
        }
    }

    protected static final class FailoverAuthenticationResultConsumer<T> extends AbstractAuthenticationResultConsumer<T> {

        static boolean logged;

        public FailoverAuthenticationResultConsumer(CompletableFuture<T> fut, boolean optional) {
            super(fut, optional);
        }

        @Override
        public void unauthorized() {
            if (!logged) {
                logMissingImpl();
            }
            failed(new ResponseException(401, "Unauthorized"));
        }

        @Override
        public void forbidden() {
            if (!logged) {
                logMissingImpl();
            }
            failed(new ResponseException(403, "Forbidden"));
        }

        private void logMissingImpl() {
            logged = true;
            new Exception("No implementation of AuthenticationResultConsumerFactory in ServiceLoader - using the "
                    + "failover implementation that does not know how any framework implements forbidden or unauthorized responses.").printStackTrace();
        }
    }

    private static class DefaultFactory extends AuthenticationResultConsumerFactory {

        @Override
        protected <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut, boolean optional) {
            return new FailoverAuthenticationResultConsumer(fut, optional);
        }

    }
}
