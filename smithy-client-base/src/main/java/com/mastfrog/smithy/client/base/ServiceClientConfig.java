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
package com.mastfrog.smithy.client.base;

import com.mastfrog.smithy.client.listeners.RequestDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.function.iteration.Iterate.each;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.smithy.client.listeners.ClientHttpMethod;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for a specific service.
 *
 * @author Tim Boudreau
 */
public final class ServiceClientConfig {

    private static final Set<String> LOGGED_BAD_DURATION_STRINGS
            = ConcurrentHashMap.newKeySet(8);

    private static final Set<RequestDecorator> DECORATORS
            = RequestDecorator.decorators();
    private final String serviceName;
    private final ClientConfig clientConfig;
    private final Map<String, String> metadata;
    private final String defaultEndpoint;
    private final String version;

    public ServiceClientConfig(String serviceName,
            ClientConfig clientConfig,
            Map<String, String> metadata,
            String defaultEndpoint, String version) {
        this.serviceName = serviceName;
        this.clientConfig = clientConfig;
        this.defaultEndpoint = defaultEndpoint;
        this.version = version;
        this.metadata = metadata;
        // Validate the endpoint early
        URI.create(endpoint());
    } // Validate the endpoint early

    public String serviceName() {
        return serviceName;
    }

    public String serviceVersion() {
        return version;
    }

    void decorateRequest(String service, ClientHttpMethod httpMethod, Optional<byte[]> body, HttpRequest.Builder bldr) throws Exception {
        each(DECORATORS, dec -> {
            dec.decorateRequest(service, httpMethod, body, bldr);
        });
    }

    ClientConfig owner() {
        return clientConfig;
    }

    @Override
    public String toString() {
        return serviceName + " " + version;
    }

    public <T> T run(ThrowingFunction<HttpClient, T> call) throws Exception {
        return clientConfig.withClient(this, call);
    }

    public ObjectMapper mapper() {
        return clientConfig.mapper();
    }

    public <R> CompletableFuture<HttpResponse<R>> request(String urlPathAndQuery,
            BodyHandler<R> handler, ThrowingConsumer<HttpRequest.Builder> c) {
        URI fullUri = uriFor(urlPathAndQuery);
        return request(fullUri, c, handler);
    }

    public <R> CompletableFuture<HttpResponse<R>> request(URI fullUri, ThrowingConsumer<HttpRequest.Builder> c, BodyHandler<R> handler) {
        System.out.println("REQUEST " + fullUri + " on " + Thread.currentThread().getName());
        try {
            return run(client -> {
                HttpRequest.Builder bldr = HttpRequest.newBuilder(fullUri);
                bldr.header("user-agent", serviceName + "-Client-" + version);
                c.accept(bldr);
                HttpRequest req = bldr.build();
                CompletableFuture<HttpResponse<R>> result = client.sendAsync(req, handler);
                return result;
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            return CompletableFuture.failedFuture(ex);
        }
    }

    private URI uriFor(String urlPathAndQuery) {
        if (urlPathAndQuery != null && (urlPathAndQuery.startsWith("http:") || urlPathAndQuery.startsWith("https:"))) {
            return URI.create(urlPathAndQuery);
        }
        String ep = endpoint();
        if (urlPathAndQuery == null) {
            return URI.create(ep);
        }
        if (urlPathAndQuery.length() > 0 && urlPathAndQuery.charAt(0) == '/') {
            urlPathAndQuery = urlPathAndQuery.substring(1);
        }
        return URI.create(ep + urlPathAndQuery);
    }

    public String endpoint() {
        String result = metadata.getOrDefault("endpoint", defaultEndpoint);
        if (!result.isEmpty() && result.charAt(result.length() - 1) != '/') {
            result = result + "/";
        }
        return result;
    }

    public final HttpClient client() {
        return clientConfig.client();
    }

    public final Optional<String> metadata(String key) {
        return Optional.ofNullable(metadata.get(notNull("key", key)))
                .or(() -> clientConfig.metadata(key));
    }

    public final Duration duration(String metadataKey, Duration defaultValue) {
        return metadata(metadataKey).map(dur -> {
            try {
                return Duration.parse(dur);
            } catch (Exception | Error e) {
                if (LOGGED_BAD_DURATION_STRINGS.add(metadataKey + "=" + dur)) {
                    e.printStackTrace();
                }
                return defaultValue;
            }
        }).orElse(defaultValue);
    }
}
