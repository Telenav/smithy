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
package com.mastfrog.smithy.client.listeners;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class ClientHttpMethod {

    private final String name;

    public static final ClientHttpMethod GET
            = new ClientHttpMethod("GET");
    public static final ClientHttpMethod HEAD
            = new ClientHttpMethod("HEAD");
    public static final ClientHttpMethod PUT
            = new ClientHttpMethod("PUT");
    public static final ClientHttpMethod POST
            = new ClientHttpMethod("POST");
    public static final ClientHttpMethod DELETE
            = new ClientHttpMethod("DELETE");
    public static final ClientHttpMethod OPTIONS
            = new ClientHttpMethod("OPTIONS");

    private ClientHttpMethod(String name) {
        this.name = notNull("name", name);
    }

    public static ClientHttpMethod httpMethod(String methodName) {
        switch (methodName) {
            case "GET":
            case "get":
                return GET;
            case "HEAD":
            case "head":
                return HEAD;
            case "PUT":
            case "put":
                return PUT;
            case "POST":
            case "post":
                return POST;
            case "DELETE":
            case "delete":
                return DELETE;
            case "OPTIONS":
            case "options":
                return OPTIONS;
            default:
                return new ClientHttpMethod(methodName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || ClientHttpMethod.class != o.getClass()) {
            return false;
        }
        return ((ClientHttpMethod) o).name.equalsIgnoreCase(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name.toUpperCase();
    }

    public HttpRequest.Builder apply(HttpRequest.Builder bldr, BodyPublisher pub) {
        return apply(bldr, Optional.ofNullable(pub));
    }

    public HttpRequest.Builder apply(HttpRequest.Builder bldr, Optional<BodyPublisher> pub) {
        switch (toString()) {
            case "DELETE":
                if (pub.isPresent()) {
                    return bldr.method(name, pub.get());
                } else {
                    return bldr.DELETE();
                }
            case "GET":
                if (pub.isPresent()) {
                    return bldr.method("GET", pub.get());
                } else {
                    return bldr.GET();
                }
            case "POST":
                return bldr.method("POST", pub.get());
            default:
                return bldr.method(name, pub.orElse(noBody()));
        }
    }
}
