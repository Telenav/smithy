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
package com.telenav.smithy.vertx.periodic.metrics;

import com.telenav.periodic.metrics.BuiltInMetrics;
import com.telenav.periodic.metrics.MetricsSink;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.strings.Strings;
import static com.telenav.periodic.metrics.BuiltInMetrics.DB_RESETS;
import static com.telenav.periodic.metrics.BuiltInMetrics.DB_RESPONSES;
import static com.telenav.periodic.metrics.BuiltInMetrics.NET_CLIENT_BYTES_WRITTEN;
import static com.telenav.periodic.metrics.BuiltInMetrics.NET_CLIENT_RESETS;
import static com.telenav.periodic.metrics.BuiltInMetrics.NET_CLIENT_RESPONSES;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.DatagramSocketMetrics;
import io.vertx.core.spi.metrics.EventBusMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.metrics.HttpServerMetrics;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import static java.lang.System.currentTimeMillis;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Implementation of VertxMetricsFactory which emits metrics into a MetricsSink.
 *
 * @author Tim Boudreau
 */
final class PeriodicVertxMetrics implements VertxMetricsFactory {

    private final Settings settings;
    private final MetricsSink sink;
    private final ClientTimingConsumer clientTimings;

    public PeriodicVertxMetrics(Settings settings, MetricsSink sink,
            ClientTimingConsumer clientTimings) {
        this.settings = settings;
        this.sink = sink;
        this.clientTimings = clientTimings;
    }

    @Override
    public VertxMetrics metrics(VertxOptions vo) {
        return new VMX(settings, sink, clientTimings);
    }

    static class VMX implements VertxMetrics {

        private final Settings settings;
        private final MetricsSink sink;
        private final ClientTimingConsumer clientTimings;

        VMX(Settings settings, MetricsSink sink, ClientTimingConsumer clientTimings) {
            this.settings = settings;
            this.sink = sink;
            this.clientTimings = clientTimings;
        }

        @Override
        public HttpServerMetrics<?, ?, ?> createHttpServerMetrics(HttpServerOptions options, SocketAddress localAddress) {
            return new HTTPMX(sink);
        }

        @Override
        public EventBusMetrics createEventBusMetrics() {
            return null;
        }

        @Override
        public ClientMetrics<?, ?, ?, ?> createClientMetrics(SocketAddress remoteAddress, String type, String namespace) {
            return new CM(sink, "db".equals(namespace) || "sql".equals(type), clientTimings, namespace);
        }

        @Override
        public HttpClientMetrics<?, ?, ?, ?> createHttpClientMetrics(HttpClientOptions options) {
            return null;
        }

        @Override
        public TCPMetrics<?> createNetServerMetrics(NetServerOptions options, SocketAddress localAddress) {
            return null;
        }

        @Override
        public TCPMetrics<?> createNetClientMetrics(NetClientOptions options) {
            return new NCMX(sink);
        }

        @Override
        public DatagramSocketMetrics createDatagramSocketMetrics(DatagramSocketOptions options) {
            return new DGMX(sink);
        }

        @Override
        public PoolMetrics<?> createPoolMetrics(String poolType, String poolName, int maxPoolSize) {
            return null;
        }

        @Override
        public void vertxCreated(Vertx vertx) {
            // do nothing
        }
    }

    static class CM implements ClientMetrics<NetClientMetric, Long, Object, Object> {

        private final MetricsSink sink;
        private final boolean db;
        private final ClientTimingConsumer clientTimings;
        private final String namespace;
        private final AtomicLong lngs = new AtomicLong();

        public CM(MetricsSink sink, boolean db, ClientTimingConsumer clientTimings, String namespace) {
            this.sink = sink;
            this.db = db;
            this.clientTimings = clientTimings;
            this.namespace = namespace;
        }

        private final Long next() {
            return lngs.incrementAndGet();
        }

        @Override
        public Long enqueueRequest() {
            return next();
        }

        @Override
        public void dequeueRequest(Long taskMetric) {
            ClientMetrics.super.dequeueRequest(taskMetric);
        }

        @Override
        public NetClientMetric requestBegin(String uri, Object request) {
            sink.onIncrement(db ? BuiltInMetrics.DB_REQUESTS : BuiltInMetrics.NET_CLIENT_REQUESTS);
            return new NetClientMetric(request);
        }

        @Override
        public void requestEnd(NetClientMetric requestMetric, long bytesWritten) {
            requestMetric.touch();
            if (bytesWritten < 0) {
                // Postgres driver always passes -1
                return;
            }
            sink.onMetric(NET_CLIENT_BYTES_WRITTEN, bytesWritten);
        }

        @Override
        public void responseBegin(NetClientMetric requestMetric, Object response) {
//            System.out.println("RESP BEGIN " + response.getClass().getName() + " " + response + " " + requestMetric);
        }

        @Override
        public void requestReset(NetClientMetric requestMetric) {
            sink.onIncrement(db ? DB_RESETS : NET_CLIENT_RESETS);
        }

        @Override
        public void requestEnd(NetClientMetric requestMetric) {
            requestEnd(requestMetric, -1);
        }

        @Override
        public void responseEnd(NetClientMetric requestMetric) {
            responseEnd(requestMetric, -1);
        }

        @Override
        public void responseEnd(NetClientMetric requestMetric, long bytesRead) {
            sink.onIncrement(db ? DB_RESPONSES : NET_CLIENT_RESPONSES);
            if (bytesRead > 0) {
                // Postgres driver always passes -1
                sink.onMetric(BuiltInMetrics.NET_CLIENT_BYTES_READ, bytesRead);
            }
            if (!(clientTimings instanceof NoOpClientTimingConsumer)) {
                requestMetric.withAges((age, sinceSend) -> {
                    clientTimings.onTiming(namespace, age, sinceSend, requestMetric.req);
                });
            }
        }
    }

    static class NetClientMetric {

        private final long start = currentTimeMillis();
        private volatile long touched;
        private final Object req;

        NetClientMetric(Object req) {
            this.req = req;
        }

        void touch() {
            touched = currentTimeMillis();
        }

        Duration age() {
            return Duration.ofMillis(currentTimeMillis() - start);
        }

        void withAges(BiConsumer<Duration, Duration> c) {
            long now = currentTimeMillis();
            Duration age = Duration.ofMillis(now - start);
            Duration sinceSend = Duration.ofMillis(now - touched);
            c.accept(age, sinceSend);
        }

        @Override
        public String toString() {
            return age() + " " + Strings.elide(Objects.toString(req), 16);
        }
    }

    static class NCMX implements TCPMetrics<Met> {

        private final MetricsSink sink;

        NCMX(MetricsSink sink) {
            this.sink = sink;
        }

        private long nextId() {
            return System.currentTimeMillis();
        }

        @Override
        public Met connected(SocketAddress remoteAddress, String remoteName) {
            sink.onIncrement(BuiltInMetrics.NET_CONNECTS);
            return new Met();
        }

        @Override
        public void disconnected(Met socketMetric, SocketAddress remoteAddress) {
            sink.onIncrement(BuiltInMetrics.NET_DISCONNECTS);
        }

        @Override
        public void bytesRead(Met socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.NET_BYTES_READ, numberOfBytes);
        }

        @Override
        public void bytesWritten(Met socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.NET_BYTES_WRITTEN, numberOfBytes);
        }

        @Override
        public void exceptionOccurred(Met socketMetric, SocketAddress remoteAddress, Throwable t) {
            sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
        }
    }

    static class DGMX implements DatagramSocketMetrics {

        private final MetricsSink sink;

        public DGMX(MetricsSink sink) {
            this.sink = sink;
        }

        @Override
        public void bytesRead(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.DATAGRAM_BYTES_READ, numberOfBytes);
        }

        @Override
        public void bytesWritten(Void socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.DATAGRAM_BYTES_WRITTEN, numberOfBytes);
        }

        @Override
        public void exceptionOccurred(Void socketMetric, SocketAddress remoteAddress, Throwable t) {
            sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
        }
    }

    static class HTTPMX implements HttpServerMetrics<Met, Met, Met> {

        private final MetricsSink sink;

        HTTPMX(MetricsSink sink) {
            this.sink = sink;
        }

        private Met next() {
            return new Met();
        }

        @Override
        public Met requestBegin(Met socketMetric, HttpRequest request) {
            sink.onIncrement(BuiltInMetrics.HTTP_REQUESTS);
            return next();
        }

        @Override
        public void requestReset(Met requestMetric) {
            sink.onIncrement(BuiltInMetrics.HTTP_REQUEST_RESET);
        }

        @Override
        public void responseEnd(Met requestMetric, HttpResponse response, long bytesWritten) {
            sink.onIncrement(BuiltInMetrics.HTTP_RESPONSES_COMPLETED);
        }

        @Override
        public void responseBegin(Met requestMetric, HttpResponse response) {
            sink.onIncrement(BuiltInMetrics.HTTP_RESPONSES_INITIATED);
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                sink.onIncrement(BuiltInMetrics.HTTP_NON_ERROR_RESPONSES);
            } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                sink.onIncrement(BuiltInMetrics.HTTP_CLIENT_ERROR_RESPONSES);
            } else if (response.statusCode() >= 500) {
                sink.onIncrement(BuiltInMetrics.HTTP_SERVER_ERROR_RESPONSES);
            }
        }

        @Override
        public Met responsePushed(Met socketMetric, HttpMethod method, String uri, HttpResponse response) {
            return next();
        }

        @Override
        public Met connected(Met socketMetric, Met requestMetric, ServerWebSocket serverWebSocket) {
            return next();
        }

        @Override
        public Met connected(SocketAddress remoteAddress, String remoteName) {
            return next();
        }

        @Override
        public void bytesRead(Met socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.HTTP_BYTES_READ, numberOfBytes);
        }

        @Override
        public void bytesWritten(Met socketMetric, SocketAddress remoteAddress, long numberOfBytes) {
            sink.onMetric(BuiltInMetrics.HTTP_BYTES_WRITTEN, numberOfBytes);
        }

        @Override
        public void exceptionOccurred(Met socketMetric, SocketAddress remoteAddress, Throwable t) {
            sink.onIncrement(BuiltInMetrics.EXCEPTION_OCCURRED);
        }

        @Override
        public void close() {
            // do nothing
        }
    }

    static class Met {
        // need a distinct metric object, but that's it
    }

}
