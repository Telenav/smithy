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
package com.telenav.smithy.client.base;

import com.telenav.smithy.client.listeners.RequestDecorator;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.function.iteration.Iterate.each;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.telenav.smithy.client.listeners.ClientHttpMethod;

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
            String defaultEndpoint,
            String version) {
        this.serviceName = serviceName;
        this.clientConfig = clientConfig;
        this.defaultEndpoint = defaultEndpoint;
        this.version = version;
        this.metadata = metadata;
        // Validate the endpoint early
        URI.create(endpoint());
    }

    public String serviceName() {
        return serviceName;
    }

    public String serviceVersion() {
        return version;
    }

    public String outboundContentType() {
        return metadata("contentType").orElse("application/json;charset=utf-8");
    }

    void decorateRequest(String service, ClientHttpMethod httpMethod, Optional<byte[]> body,
                         HttpRequest.Builder bldr) throws Exception {
        if (body.isPresent()) {
            bldr.header("content-type", outboundContentType());
        }
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
        try {
            return run(client -> {
                HttpRequest.Builder bldr = HttpRequest.newBuilder(fullUri);
                bldr.header("user-agent", serviceName.toLowerCase() + "-client-" + version);
                c.accept(bldr);
                HttpRequest req = bldr.build();
                return client.sendAsync(req, handler);
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
