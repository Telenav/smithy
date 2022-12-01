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

import com.mastfrog.smithy.http.SmithyRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import static io.netty.util.CharsetUtil.UTF_8;
import io.vertx.core.http.HttpServerRequest;
import java.util.Arrays;
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
