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

import com.telenav.smithy.http.SmithyRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import static io.netty.util.CharsetUtil.UTF_8;
import io.vertx.core.http.HttpServerRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Wraps a vertx request as a smithy request.
 *
 * @author Tim Boudreau
 */
public final class VertxRequestAdapter implements SmithyRequest {

    private final HttpServerRequest request;

    public VertxRequestAdapter(HttpServerRequest request) {
        this.request = request;
    }

    public static SmithyRequest smithyRequest(HttpServerRequest request) {
        return new VertxRequestAdapter(request);
    }

    @Override
    public Optional<CharSequence> httpHeader(CharSequence name) {
        return Optional.ofNullable(request.headers().get(name));
    }

    @Override
    public Set<? extends CharSequence> httpHeaderNames() {
        return request.headers().names();
    }

    @Override
    public Optional<CharSequence> uriPathElement(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Negative uri path index");
        }
        String pth = request.path();
        if (pth.isEmpty()) {
            return Optional.empty();
        }
        if (pth.charAt(0) == '/') {
            pth = pth.substring(1);
        }
        String[] parts = pth.split("\\/+");
        if (index >= parts.length) {
            return Optional.empty();
        }
        return Optional.of(parts[index]);
    }

    @Override
    public Optional<CharSequence> uriQueryParameter(CharSequence name, boolean decode) {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri(), UTF_8, true);
        List<String> values = decoder.parameters().get(name.toString());
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    @Override
    public String httpMethod() {
        return request.method().name();
    }

    @Override
    public String requestUri(boolean preferHeaders) {
        return request.uri();
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return SmithyRequest.super.unwrap(type)
                .or(() -> type.isInstance(request)
                ? Optional.of(type.cast(request))
                : Optional.empty());
    }

}
