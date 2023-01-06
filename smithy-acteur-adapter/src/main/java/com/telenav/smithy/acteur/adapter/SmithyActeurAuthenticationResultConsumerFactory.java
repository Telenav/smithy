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
package com.telenav.smithy.acteur.adapter;

import com.mastfrog.acteur.errors.ResponseException;
import com.telenav.smithy.http.AuthenticationResultConsumer;
import com.telenav.smithy.http.AuthenticationResultConsumerFactory;
import com.mastfrog.util.service.ServiceProvider;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import java.util.concurrent.CompletableFuture;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(AuthenticationResultConsumerFactory.class)
public final class SmithyActeurAuthenticationResultConsumerFactory extends AuthenticationResultConsumerFactory {

    @Override
    protected <T> AuthenticationResultConsumer<T> create(CompletableFuture<T> fut, boolean optional) {
        return new ConsumerImpl<T>(fut, optional);
    }

    private static class ConsumerImpl<T> extends AbstractAuthenticationResultConsumer<T> {

        ConsumerImpl(CompletableFuture<T> fut, boolean optional) {
            super(fut, optional);
        }

        @Override
        public void unauthorized() {
            failed(new ResponseException(UNAUTHORIZED, "Unauthorized"));
        }

        @Override
        public void forbidden() {
            failed(new ResponseException(FORBIDDEN, "Unauthorized"));
        }
    }

}
