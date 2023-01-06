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
package com.telenav.smithy.client.listeners;

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
