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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.smithy.http.HeaderSpec;
import com.mastfrog.smithy.http.SmithyResponse;
import static com.mastfrog.util.preconditions.Exceptions.chuck;
import com.mastfrog.util.strings.Strings;
import io.netty.buffer.ByteBuf;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import static io.vertx.core.buffer.Buffer.buffer;
import io.vertx.core.http.HttpServerResponse;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class VertxResponseAdapter<T> implements SmithyResponse<T> {

    private final HttpServerResponse response;
    private final ObjectMapper mapper;
    private Future<Void> future;

    public VertxResponseAdapter(HttpServerResponse response, ObjectMapper mapper) {
        this.response = response;
        this.mapper = mapper;
    }

    public static <T> SmithyResponse<T> smithyResponse(HttpServerResponse response, ObjectMapper mapper) {
        return new VertxResponseAdapter<>(response, mapper);
    }

    @Override
    public SmithyResponse<T> add(HeaderSpec header, Object value) {
        response.putHeader(header.name().toString(), header.toCharSequence(value));
        return this;
    }

    @Override
    public SmithyResponse<T> status(int code) {
        response.setStatusCode(code);
        return this;
    }

    public synchronized Future<Void> future() {
        return future;
    }

    @Override
    public synchronized void complete(Object responseObject) {
        if (responseObject == null) {
            future = response.send();
        } else if (responseObject instanceof ByteBuf) {
            future = response.send(buffer((ByteBuf) responseObject));
        } else if (responseObject instanceof CharSequence) {
            future = response.send(responseObject.toString());
        } else if (responseObject instanceof byte[]) {
            future = response.send(buffer((byte[]) responseObject));
        } else if (responseObject instanceof Buffer) {
            future = response.send((Buffer) responseObject);
        } else {
            try {
                future = response.send(buffer(mapper.writeValueAsBytes(responseObject)));
            } catch (JsonProcessingException ex) {
                chuck(ex);
            }
        }
    }

    @Override
    public synchronized void completeExceptionally(Throwable thrown) {
        response.setStatusCode(500);
        future = response.send(buffer(Strings.toString(thrown)));
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return SmithyResponse.super.unwrap(type)
                .or(() -> type.isInstance(response)
                ? Optional.of(type.cast(response))
                : Optional.empty());
    }
}
