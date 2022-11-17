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
package com.telenav.smithy.smithy.acteur.adapter;

import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.headers.HeaderValueType;
import com.mastfrog.smithy.http.SmithyRequest;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Optional;
import static java.util.Optional.empty;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class SmithyRequestAdapter implements SmithyRequest {

    private final HttpEvent event;
    private final Closables closables;

    private SmithyRequestAdapter(HttpEvent event, Closables clos) {
        this.event = event;
        this.closables = clos;
    }

    static SmithyRequestAdapter wrap(HttpEvent evt, Closables clos) {
        return new SmithyRequestAdapter(evt, clos);
    }

    @Override
    public void onClose(Runnable run) {
        closables.add(run);
    }

    @Override
    public <T> Optional<T> unwrap(Class<T> type) {
        return SmithyRequest.super.unwrap(type)
                .or(() -> {
                    if (type.isInstance(event)) {
                        return Optional.of(type.cast(event));
                    }
                    if (type.isInstance(closables)) {
                        return Optional.of(type.cast(closables));
                    }
                    return empty();
                });
    }

    public <T> T header(HeaderValueType<T> value) {
        return event.header(value);
    }

    public String stringContent() throws IOException {
        return event.stringContent();
    }

    public SocketAddress remoteAddress() {
        return event.remoteAddress();
    }

    public <T> T jsonContent(Class<T> type) throws Exception {
        return event.jsonContent(type);
    }

    public ByteBuf content() throws IOException {
        return event.content();
    }

    public <T> Optional<T> httpHeader(HeaderValueType<T> header) {
        return event.httpHeader(header);
    }

    @Override
    public Optional<CharSequence> httpHeader(CharSequence name) {
        return event.httpHeader(name);
    }

    public Optional<CharSequence> uriAnchor() {
        return event.uriAnchor();
    }

    @Override
    public Optional<CharSequence> uriPathElement(int index) {
        if (event.path().size() > index) {
            return Optional.<CharSequence>of(event.path().getElement(index).toString());
        }
        return Optional.empty();
//        return event.uriPathElement(index);
    }

    @Override
    public Optional<CharSequence> uriQueryParameter(CharSequence name, boolean decode) {
        return event.uriQueryParameter(name, decode);
    }

    @Override
    public Optional<CharSequence> uriQueryParameter(CharSequence name) {
        return event.uriQueryParameter(name);
    }

    @Override
    public <N extends Number> Optional<N> uriQueryParameter(CharSequence name, Class<N> type) {
        return event.uriQueryParameter(name, type);
    }

    @Override
    public Optional<Boolean> booleanUriQueryParameter(CharSequence name) {
        return event.booleanUriQueryParameter(name);
    }

    @Override
    public <N extends Number> Optional<N> uriPathElement(int index, Class<N> type) {
        return event.uriPathElement(index, type);
    }

    public <N extends Number> Optional<N> uriAnchor(Class<N> type) {
        return event.uriAnchor(type);
    }

    @Override
    public String httpMethod() {
        return event.httpMethod();
    }

    @Override
    public boolean isMethod(Object o) {
        return event.isMethod(o);
    }

    @Override
    public String requestUri(boolean preferHeaders) {
        return event.requestUri(preferHeaders);
    }

    @Override
    public String requestUri() {
        return event.requestUri();
    }

    @Override
    public Set<? extends CharSequence> httpHeaderNames() {
        return event.httpHeaderNames();
    }

    @Override
    public String toString() {
        return event.toString();
    }

}
