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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.smithy.client.listeners.ClientHttpMethod;
import com.mastfrog.util.preconditions.Exceptions;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import com.mastfrog.smithy.client.result.ServiceResult;
import static com.mastfrog.util.preconditions.Exceptions.chuck;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 *
 * @author timb
 */
public abstract class BaseServiceClient<S> {

    private static final Duration DEFAULT_MAX_DURATION
            = Duration.ofMinutes(30);

    private final ServiceClientConfig config;
    private final ObjectMapper mapper;

    protected BaseServiceClient(String apiName, String version, String defaultEndpoint) {
        this(ClientConfig.get().get(apiName, defaultEndpoint, version));
    }

    protected BaseServiceClient(ServiceClientConfig config) {
        this.config = config;
        mapper = configureObjectMapper(config.mapper());
    }

    public abstract S withEndpoint(String endpoint);

    @SuppressWarnings("unchecked")
    protected S cast() {
        return (S) this;
    }

    public final String serviceName() {
        return config.serviceName();
    }

    public final String serviceVersion() {
        return config.serviceVersion();
    }

    /**
     * Provide any customizations to the ObjectMapper for JSON serialization
     * here - they will not be shared with other service clients.
     *
     * @param orig An ObjectMapper
     * @return an ObjectMapper
     */
    protected ObjectMapper configureObjectMapper(ObjectMapper orig) {
        return orig;
    }

    /**
     * Get the ObjectMapper for use with serialization / deserialization.
     *
     * @return An ObjectMapper
     */
    protected final ObjectMapper mapper() {
        return mapper.copy();
    }

    /**
     * Get the configuration of this service instance.
     *
     * @return A configuration
     */
    public final ServiceClientConfig config() {
        return config;
    }

    /**
     * Perform some operation that requires an HttpClient, and ensure that this
     * client's config is the context used for all calls within the closure of
     * the passed function, or HTTP calls generated by it.
     *
     * @param <T> A return type
     * @param func A function
     * @return The result of the function
     * @throws Exception - undeclared exceptions if the function throws
     */
    protected final <T> T withHttpClient(ThrowingFunction<HttpClient, T> func) {
        try {
            return config.run(func);
        } catch (Exception ex) {
            return Exceptions.chuck(ex);
        }
    }

    /**
     * Perform an HTTP get.
     *
     * @param <T> The return type
     * @param urlBase The path (and possibly query) portion of the URL to access
     * @param type The type that should be deserialized as the return value.
     * @return A future that will return a service result over a possible
     * instance of T
     */
    protected final <T> CompletableFuture<ServiceResult<T>> get(String urlBase,
            Class<T> type) {
        return requestWithBody(ClientHttpMethod.GET, null, urlBase, type, null);
    }

    protected final <T> CompletableFuture<ServiceResult<T>> get(String urlBase,
            Class<T> type, ThrowingConsumer<HttpRequest.Builder> c) {
        return requestWithBody(ClientHttpMethod.GET, null, urlBase, type, (bldr, _ignored) -> {
            if (c != null) {
                c.accept(bldr);
            }
        });
    }

    protected final <T> CompletableFuture<ServiceResult<T>> delete(String urlBase,
            Class<T> type) {
        return requestWithBody(ClientHttpMethod.DELETE, null, urlBase, type, null);
    }

    protected final <T> CompletableFuture<ServiceResult<T>> delete(String urlBase,
            Class<T> type, ThrowingConsumer<HttpRequest.Builder> c) {
        return requestWithBody(ClientHttpMethod.DELETE, null, urlBase, type, (bldr, _ignored) -> {
            if (c != null) {
                c.accept(bldr);
            }
        });
    }

    protected final <T> CompletableFuture<ServiceResult<T>> post(String urlBase, Class<T> type) {
        return requestWithBody(ClientHttpMethod.POST, null, urlBase, type, null);
    }

    protected final <I, T> CompletableFuture<ServiceResult<T>> post(I input, String urlBase,
            Class<T> type) {
        return post(input, urlBase, type, null);
    }

    protected final <I, T> CompletableFuture<ServiceResult<T>> post(I input, String urlBase,
            Class<T> type, ThrowingBiConsumer<HttpRequest.Builder, byte[]> c) {
        return requestWithBody(ClientHttpMethod.POST, input, urlBase, type, c);
    }

    protected final <I, T> CompletableFuture<ServiceResult<T>> put(I input, String urlBase, Class<T> type) {
        return put(input, urlBase, type, null);
    }

    protected final <I, T> CompletableFuture<ServiceResult<T>> put(I input, String urlBase, Class<T> type, ThrowingBiConsumer<HttpRequest.Builder, byte[]> c) {
        return requestWithBody(ClientHttpMethod.PUT, input, urlBase, type, c);
    }

    private final <I, T> CompletableFuture<ServiceResult<T>> requestWithBody(
            ClientHttpMethod method, I input, String urlBase,
            Class<T> responseBodyType,
            ThrowingBiConsumer<HttpRequest.Builder, byte[]> c) {
        try {
            // Converts a byte[] based response into one decoded by Jackson
            JacksonBodyHandlerWrapper<T> handler = new JacksonBodyHandlerWrapper<>(mapper, responseBodyType);
            // Make our HTTP request
            CompletableFuture<HttpResponse<ServiceResult<T>>> result = config.request(urlBase, handler, bldr -> {
                            byte[] bytes;
                            if (input != null) {
                                bytes = mapper.writeValueAsBytes(input);
                            } else {
                    // Null may be a perfectly valid response
                                bytes = null;
                            }
                            if (c != null) {
                                c.accept(bldr, bytes);
                            }
                            config.decorateRequest(urlBase, method, Optional.ofNullable(bytes),
                                    method.apply(bldr, bytes == null ? BodyPublishers.noBody()
                                                                        : BodyPublishers.ofByteArray(bytes))
                            );
            });
            // And wrapper that as a future returning a service result
            CompletableFuture<ServiceResult<T>> res = result.handleAsync((resp, thrown) -> {
                Throwable th = thrown instanceof ExecutionException
                        && thrown.getCause() != null ? thrown.getCause() : thrown;
                if (th != null) {
                    if (th instanceof CancellationException) {
                        // We were cancelled
                        return ServiceResult.cancelled();
                    } else if (th instanceof TimeoutException) {
                        // We timed out
                        return ServiceResult.timeout();
                    }
                    // Something else went wrong
                    return ServiceResult.thrown(th);
                }
                // Just unwrap the service result body
                return resp.body();
            }, config.owner().executor());
            // Ensure we honor the client timeout
            Duration timeout = config.duration("requestMaxDuration", DEFAULT_MAX_DURATION);
            res.completeOnTimeout(ServiceResult.timeout(), timeout.toMillis(), TimeUnit.MILLISECONDS);
            return res;
        } catch (Exception | Error e) {
            return CompletableFuture.completedFuture(ServiceResult.thrown(e));
        }
    }

    protected URIBuilder uri() {
        return new URIBuilder();
    }

    protected URIBuilder uri(String base) {
        return new URIBuilder(base);
    }

    protected String endpoint() {
        return config.endpoint();
    }

    /**
     * URI builder used by generated code - this exists mainly to simplify the
     * generated code somewhat, which has to target both raw types and wrapper
     * types and those wrapped in optional or not; with special handling for
     * collections.
     */
    protected static class URIBuilder {

        private final List<URIElement> contents = new ArrayList<>(16);
        private final Map<String, URIElement> query = new TreeMap<>();

        URIBuilder(Object... initial) {
            for (Object ini : initial) {
                String item = Objects.toString(ini);
                for (String part : item.split("/+")) {
                    if (!part.isEmpty()) {
                        contents.add(new FixedURIElement(part));
                    }
                }
            }
        }

        public URIBuilder add(String component) {
            contents.add(new FixedURIElement(component));
            return this;
        }

        public URIBuilder add(Supplier<? extends Object> supplier) {
            contents.add(new SupplierURIElement(supplier));
            return this;
        }

        public URIBuilder putQueryParameter(String name, Supplier<? extends Object> supplier) {
            query.put(name, new SupplierURIElement(supplier));
            return this;
        }

        public URIBuilder putOptionalQueryParameter(String name, Supplier<? extends Optional<? extends Object>> supplier) {
            query.put(name, new SupplierURIElement(supplier));
            return this;
        }

        public URIBuilder putQueryParameter(String name, String value) {
            query.put(name, new FixedURIElement(value));
            return this;
        }

        public URI build(String uriBase) {
            return URI.create(toString(uriBase));
        }

        @Override
        public String toString() {
            return toString("");
        }

        public String toString(String urlBase) {
            StringBuilder sb = new StringBuilder(urlBase);
            Consumer<URIElement> appendPath = item -> {
                if (!item.hasValue()) {
                    return;
                }
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') {
                    sb.append('/');
                }
                String s = Objects.toString(item);
                if (s.length() == 0) {
                    throw new IllegalArgumentException("0-length uri element after '" + sb + "' for " + item);
                }
                if (s.charAt(0) == '/') {
                    s = s.substring(1);
                }
                if (s.length() == 0) {
                    throw new IllegalArgumentException("0-length uri element after '" + sb + "' for " + item);
                }
                if (s.charAt(s.length() - 1) == '/') {
                    s = s.substring(0, s.length() - 1);
                }
                if (s.length() == 0) {
                    throw new IllegalArgumentException("0-length uri element after '" + sb + "' for " + item);
                }
                sb.append(URLEncoder.encode(s, UTF_8));
            };
            for (URIElement o : contents) {
                appendPath.accept(o);
            }
            if (!query.isEmpty()) {
                List<Map.Entry<String, URIElement>> items = new ArrayList<>(query.entrySet());
                for (int i = 0; i < items.size(); i++) {
                    Map.Entry<String, URIElement> el = items.get(i);
                    if (!el.getValue().hasValue()) {
                        continue;
                    }
                    switch (i) {
                        case 0:
                            sb.append('?');
                            break;
                        default:
                            sb.append('&');
                            break;
                    }
                    sb.append(URLEncoder.encode(el.getKey(), UTF_8));
                    sb.append('=');
                    sb.append(URLEncoder.encode(el.getValue().toString(), UTF_8));
                }
            }
            return sb.toString();
        }

        private static abstract class URIElement {

            abstract boolean hasValue();
        }

        private static class FixedURIElement extends URIElement {

            private final Object item;

            public FixedURIElement(Object item) {
                this.item = item;
            }

            @Override
            boolean hasValue() {
                return item != null;
            }

            public String toString() {
                return Objects.toString(item);
            }
        }

        private static class SupplierURIElement extends URIElement {

            private final Supplier<? extends Object> supplier;
            private Optional<Object> obj;

            public SupplierURIElement(Supplier<? extends Object> supplier) {
                this.supplier = supplier;
            }

            private Optional<? extends Object> get() {
                if (obj != null) {
                    return obj;
                }
                Object o = supplier.get();
                if (o instanceof Optional<?>) {
                    Optional<?> opt = (Optional<?>) o;
                    return opt;
                }
                return obj = Optional.ofNullable(supplier.get());
            }

            @Override
            public String toString() {
                return get().map(URIBuilder::stringify).orElse("");
            }

            @Override
            boolean hasValue() {
                Object o = get();
                if (o instanceof Optional<?>) {
                    return (((Optional<?>) o)).isPresent();
                }
                return o != null;
            }
        }

        private static class OptionalURIElement extends URIElement {

            private final Supplier<? extends Optional<? extends Object>> supplier;
            private Optional<? extends Object> value;

            OptionalURIElement(Supplier<? extends Optional<? extends Object>> supplier) {
                this.supplier = supplier;
            }

            Optional<? extends Object> get() {
                if (value != null) {
                    return value;
                }
                value = supplier.get();
                if (value == null) {
                    value = Optional.empty();
                }
                return value;
            }

            @Override
            boolean hasValue() {
                return get().isPresent();
            }

            @Override
            public String toString() {
                return get().map(URIBuilder::stringify).orElse("");
            }
        }

        private static String stringify(Object obj) {
            // We are converting to URI elements here, so convert
            // collections to comma-delimited and similar
            if (obj == null) {
                return "";
            }
            while (obj instanceof Optional<?>) {
                Optional<?> opt = (Optional<?>) obj;
                if (!opt.isPresent()) {
                    return "";
                }
                obj = opt.get();
            }
            if (obj instanceof Collection<?>) {
                Collection<?> c = (Collection<?>) obj;
                if (c.isEmpty()) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                for (Object o : c) {
                    if (sb.length() > 0) {
                        sb.append(',');
                    }
                    sb.append(stringify(o));
                }
                return sb.toString();
            }
            return obj.toString();
        }
    }
}
