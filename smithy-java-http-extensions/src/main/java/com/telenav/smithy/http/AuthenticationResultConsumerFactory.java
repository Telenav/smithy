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
