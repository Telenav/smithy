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
package com.telenav.vertx.guice.scope;

import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolverGroup;
import io.vertx.core.AsyncResult;
import io.vertx.core.Closeable;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.TimeoutStream;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.impl.HttpServerImpl;
import io.vertx.core.impl.AddressResolver;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.FailoverCompleteHandler;
import io.vertx.core.impl.HAManager;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerContext;
import io.vertx.core.impl.WorkerExecutorInternal;
import io.vertx.core.impl.WorkerPool;
import io.vertx.core.impl.btc.BlockedThreadChecker;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.NetServerImpl;
import io.vertx.core.net.impl.ServerID;
import io.vertx.core.net.impl.TCPServerBase;
import io.vertx.core.net.impl.transport.Transport;
import io.vertx.core.shareddata.SharedData;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.file.FileResolver;
import io.vertx.core.spi.metrics.VertxMetrics;
import io.vertx.core.spi.tracing.VertxTracer;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Wraps the outer Vertx so scope contents are propagated. Needs to implement
 * VertxInternal and ContextInternal because some vertx extensions (such as
 * vertx-pg-driver blindly cast to those types).
 *
 * @author Tim Boudreau
 */
final class VertxWrapper implements Vertx, VertxInternal {

    private final Vertx vertx;
    private final RequestScope scope;
    private final VertxInternal internal;

    VertxWrapper(Vertx vertx, RequestScope scope) {
        if (!(vertx instanceof VertxInternal)) {
            // The Postgres driver blindly casts to VertxInternal, making
            // it fail with wrapped instances which cannot implement VertxInternal
            // as well
            throw new IllegalArgumentException("Not a VertxInternal");
        }
        this.vertx = vertx;
        this.scope = scope;
        this.internal = (VertxInternal) vertx;
    }

    private VertxInternal internal() {
        return (VertxInternal) vertx;
    }

    @Override
    public ContextInternal getOrCreateContext() {
        ContextInternal result = (ContextInternal) vertx.getOrCreateContext();
        return result;
    }

    @Override
    public NetServer createNetServer(NetServerOptions options) {
        return vertx.createNetServer(options);
    }

    @Override
    public NetServer createNetServer() {
        return vertx.createNetServer();
    }

    @Override
    public NetClient createNetClient(NetClientOptions options) {
        return vertx.createNetClient(options);
    }

    @Override
    public NetClient createNetClient() {
        return vertx.createNetClient();
    }

    @Override
    public HttpServer createHttpServer(HttpServerOptions options) {
        return vertx.createHttpServer(options);
    }

    @Override
    public HttpServer createHttpServer() {
        return vertx.createHttpServer();
    }

    @Override
    public HttpClient createHttpClient(HttpClientOptions options) {
        return vertx.createHttpClient(options);
    }

    @Override
    public HttpClient createHttpClient() {
        return vertx.createHttpClient();
    }

    @Override
    public DatagramSocket createDatagramSocket(DatagramSocketOptions options) {
        return vertx.createDatagramSocket(options);
    }

    @Override
    public DatagramSocket createDatagramSocket() {
        return vertx.createDatagramSocket();
    }

    @Override
    public FileSystem fileSystem() {
        return vertx.fileSystem();
    }

    @Override
    public EventBus eventBus() {
        return vertx.eventBus();
    }

    @Override
    public DnsClient createDnsClient(int port, String host) {
        return vertx.createDnsClient(port, host);
    }

    @Override
    public DnsClient createDnsClient() {
        return vertx.createDnsClient();
    }

    @Override
    public DnsClient createDnsClient(DnsClientOptions options) {
        return vertx.createDnsClient(options);
    }

    @Override
    public SharedData sharedData() {
        return vertx.sharedData();
    }

    @Override
    public long setTimer(long delay, Handler<Long> handler) {
        return vertx.setTimer(delay, handler);
    }

    @Override
    public TimeoutStream timerStream(long delay) {
        return vertx.timerStream(delay);
    }

    @Override
    public long setPeriodic(long delay, Handler<Long> handler) {
        return vertx.setPeriodic(delay, handler);
    }

    @Override
    public long setPeriodic(long initialDelay, long delay, Handler<Long> handler) {
        return vertx.setPeriodic(initialDelay, delay, handler);
    }

    @Override
    public TimeoutStream periodicStream(long delay) {
        return vertx.periodicStream(delay);
    }

    @Override
    public TimeoutStream periodicStream(long initialDelay, long delay) {
        return vertx.periodicStream(initialDelay, delay);
    }

    @Override
    public boolean cancelTimer(long id) {
        return vertx.cancelTimer(id);
    }

    @Override
    public void runOnContext(Handler<Void> action) {
        vertx.runOnContext(action);
    }

    @Override
    public Future<Void> close() {
        return vertx.close();
    }

    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        vertx.close(completionHandler);
    }

    @Override
    public Future<String> deployVerticle(Verticle verticle) {
        return vertx.deployVerticle(verticle);
    }

    @Override
    public void deployVerticle(Verticle verticle, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(verticle, completionHandler);
    }

    @Override
    public Future<String> deployVerticle(Verticle verticle, DeploymentOptions options) {
        return vertx.deployVerticle(verticle, options);
    }

    @Override
    public Future<String> deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options) {
        return vertx.deployVerticle(verticleClass, options);
    }

    @Override
    public Future<String> deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options) {
        return vertx.deployVerticle(verticleSupplier, options);
    }

    @Override
    public void deployVerticle(Verticle verticle, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(verticle, options, completionHandler);
    }

    @Override
    public void deployVerticle(Class<? extends Verticle> verticleClass, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(verticleClass, options, completionHandler);
    }

    @Override
    public void deployVerticle(Supplier<Verticle> verticleSupplier, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(verticleSupplier, options, completionHandler);
    }

    @Override
    public Future<String> deployVerticle(String name) {
        return vertx.deployVerticle(name);
    }

    @Override
    public void deployVerticle(String name, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(name, completionHandler);
    }

    @Override
    public Future<String> deployVerticle(String name, DeploymentOptions options) {
        return vertx.deployVerticle(name, options);
    }

    @Override
    public void deployVerticle(String name, DeploymentOptions options, Handler<AsyncResult<String>> completionHandler) {
        vertx.deployVerticle(name, options, completionHandler);
    }

    @Override
    public Future<Void> undeploy(String deploymentID) {
        return vertx.undeploy(deploymentID);
    }

    @Override
    public void undeploy(String deploymentID, Handler<AsyncResult<Void>> completionHandler) {
        vertx.undeploy(deploymentID, completionHandler);
    }

    @Override
    public Set<String> deploymentIDs() {
        return vertx.deploymentIDs();
    }

    @Override
    public void registerVerticleFactory(VerticleFactory factory) {
        vertx.registerVerticleFactory(factory);
    }

    @Override
    public void unregisterVerticleFactory(VerticleFactory factory) {
        vertx.unregisterVerticleFactory(factory);
    }

    @Override
    public Set<VerticleFactory> verticleFactories() {
        return vertx.verticleFactories();
    }

    @Override
    public boolean isClustered() {
        return vertx.isClustered();
    }

    @Override
    public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
        vertx.executeBlocking(blockingCodeHandler, ordered, resultHandler);
    }

    @Override
    public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
        vertx.executeBlocking(blockingCodeHandler, resultHandler);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
        return vertx.executeBlocking(blockingCodeHandler, ordered);
    }

    @Override
    public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler) {
        return vertx.executeBlocking(blockingCodeHandler);
    }

    @Override
    public EventLoopGroup nettyEventLoopGroup() {
        return vertx.nettyEventLoopGroup();
    }

    @Override
    public WorkerExecutorInternal createSharedWorkerExecutor(String name) {
        return internal().createSharedWorkerExecutor(name);
    }

    @Override
    public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize) {
        return internal().createSharedWorkerExecutor(name, poolSize);
    }

    @Override
    public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime) {
        return internal().createSharedWorkerExecutor(name, poolSize, maxExecuteTime);
    }

    @Override
    public WorkerExecutorInternal createSharedWorkerExecutor(String name, int poolSize, long maxExecuteTime, TimeUnit maxExecuteTimeUnit) {
        return internal().createSharedWorkerExecutor(name, poolSize, maxExecuteTime, maxExecuteTimeUnit);
    }

    @Override
    public boolean isNativeTransportEnabled() {
        return vertx.isNativeTransportEnabled();
    }

    @Override
    public Vertx exceptionHandler(Handler<Throwable> handler) {
        return vertx.exceptionHandler(handler);
    }

    @Override
    public Handler<Throwable> exceptionHandler() {
        return vertx.exceptionHandler();
    }

    @Override
    public boolean isMetricsEnabled() {
        return vertx.isMetricsEnabled();
    }

    @Override
    public String toString() {
        return "ScopeWrapper(" + vertx + ")";
    }

    @Override
    public <T> PromiseInternal<T> promise() {
        return internal().promise();
    }

    @Override
    public <T> PromiseInternal<T> promise(Handler<AsyncResult<T>> hndlr) {
        return internal().promise(scope.wrapGeneral(hndlr));
    }

    @Override
    public long maxEventLoopExecTime() {
        return internal().maxEventLoopExecTime();
    }

    @Override
    public TimeUnit maxEventLoopExecTimeUnit() {
        return internal().maxEventLoopExecTimeUnit();
    }

    @Override
    public EventLoopGroup getEventLoopGroup() {
        return internal().getEventLoopGroup();
    }

    @Override
    public EventLoopGroup getAcceptorEventLoopGroup() {
        return internal().getAcceptorEventLoopGroup();
    }

    @Override
    public WorkerPool getWorkerPool() {
        return internal().getWorkerPool();
    }

    @Override
    public WorkerPool getInternalWorkerPool() {
        return internal().getInternalWorkerPool();
    }

    @Override
    public Map<ServerID, HttpServerImpl> sharedHttpServers() {
        return internal().sharedHttpServers();
    }

    @Override
    public Map<ServerID, NetServerImpl> sharedNetServers() {
        return internal().sharedNetServers();
    }

    @Override
    public <S extends TCPServerBase> Map<ServerID, S> sharedTCPServers(Class<S> type) {
        return internal().sharedTCPServers(type);
    }

    @Override
    public VertxMetrics metricsSPI() {
        return internal().metricsSPI();
    }

    @Override
    public Transport transport() {
        return internal().transport();
    }

    @Override
    public HttpClient createHttpClient(HttpClientOptions hco, CloseFuture cf) {
        return internal().createHttpClient(hco, cf);
    }

    @Override
    public ContextInternal getContext() {
        return new ContextWrapper(internal().getContext());
    }

    @Override
    public EventLoopContext createEventLoopContext(Deployment dplmnt, CloseFuture cf, WorkerPool wp, ClassLoader cl) {
        return internal().createEventLoopContext(dplmnt, cf, wp, cl);
    }

    @Override
    public EventLoopContext createEventLoopContext(EventLoop el, WorkerPool wp, ClassLoader cl) {
        return internal().createEventLoopContext(el, wp, cl);
    }

    @Override
    public EventLoopContext createEventLoopContext() {
        return internal().createEventLoopContext();
    }

    @Override
    public WorkerContext createWorkerContext(Deployment dplmnt, CloseFuture cf, WorkerPool wp, ClassLoader cl) {
        return internal().createWorkerContext(dplmnt, cf, wp, cl);
    }

    @Override
    public WorkerContext createWorkerContext() {
        return internal().createWorkerContext();
    }

    @Override
    public WorkerPool createSharedWorkerPool(String string, int i, long l, TimeUnit tu) {
        return internal().createSharedWorkerPool(string, i, l, tu);
    }

    @Override
    public void simulateKill() {
        internal().simulateKill();
    }

    @Override
    public Deployment getDeployment(String string) {
        return internal().getDeployment(string);
    }

    @Override
    public void failoverCompleteHandler(FailoverCompleteHandler fch) {
        internal().failoverCompleteHandler(fch);
    }

    @Override
    public boolean isKilled() {
        return internal().isKilled();
    }

    @Override
    public void failDuringFailover(boolean bln) {
        internal().failDuringFailover(bln);
    }

    @Override
    public File resolveFile(String string) {
        return internal().resolveFile(string);
    }

    @Override
    public ClusterManager getClusterManager() {
        return internal().getClusterManager();
    }

    @Override
    public HAManager haManager() {
        return internal().haManager();
    }

    @Override
    public void resolveAddress(String string, Handler<AsyncResult<InetAddress>> hndlr) {
        internal().resolveAddress(string, hndlr);
    }

    @Override
    public AddressResolver addressResolver() {
        return internal().addressResolver();
    }

    @Override
    public FileResolver fileResolver() {
        return internal().fileResolver();
    }

    @Override
    public AddressResolverGroup<InetSocketAddress> nettyAddressResolverGroup() {
        return internal().nettyAddressResolverGroup();
    }

    @Override
    public BlockedThreadChecker blockedThreadChecker() {
        return internal().blockedThreadChecker();
    }

    @Override
    public CloseFuture closeFuture() {
        return internal().closeFuture();
    }

    @Override
    public VertxTracer tracer() {
        return internal().tracer();
    }

    @Override
    public void addCloseHook(Closeable clsbl) {
        internal().addCloseHook(clsbl);
    }

    @Override
    public void removeCloseHook(Closeable clsbl) {
        internal().removeCloseHook(clsbl);
    }

    class ContextWrapper implements Context, ContextInternal {

        private final Context delegate;

        public ContextWrapper(Context delegate) {
            this.delegate = delegate;
        }

        private ContextInternal cinternal() {
            return (ContextInternal) delegate;
        }

        @Override
        public void runOnContext(Handler<Void> action) {
            delegate.runOnContext(scope.wrapGeneral(action));
        }

        @Override
        public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered, Handler<AsyncResult<T>> resultHandler) {
            delegate.executeBlocking(blockingCodeHandler, ordered, resultHandler);
        }

        @Override
        public <T> void executeBlocking(Handler<Promise<T>> blockingCodeHandler, Handler<AsyncResult<T>> resultHandler) {
            delegate.executeBlocking(blockingCodeHandler, resultHandler);
        }

        @Override
        public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
            return delegate.executeBlocking(blockingCodeHandler, ordered);
        }

        @Override
        public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler) {
            return delegate.executeBlocking(blockingCodeHandler);
        }

        @Override
        public String deploymentID() {
            return delegate.deploymentID();
        }

        @Override
        public JsonObject config() {
            return delegate.config();
        }

        @Override
        public List<String> processArgs() {
            return delegate.processArgs();
        }

        @Override
        public boolean isEventLoopContext() {
            return delegate.isEventLoopContext();
        }

        @Override
        public boolean isWorkerContext() {
            return delegate.isWorkerContext();
        }

        @Override
        public <T> T get(Object key) {
            return delegate.get(key);
        }

        @Override
        public void put(Object key, Object value) {
            delegate.put(key, value);
        }

        @Override
        public boolean remove(Object key) {
            return delegate.remove(key);
        }

        @Override
        public <T> T getLocal(Object key) {
            return delegate.getLocal(key);
        }

        @Override
        public void putLocal(Object key, Object value) {
            delegate.putLocal(key, value);
        }

        @Override
        public boolean removeLocal(Object key) {
            return delegate.removeLocal(key);
        }

        @Override
        public VertxInternal owner() {
            return VertxWrapper.this;
        }

        @Override
        public int getInstanceCount() {
            return delegate.getInstanceCount();
        }

        @Override
        public Context exceptionHandler(Handler<Throwable> handler) {
            return delegate.exceptionHandler(scope.wrapGeneral(handler));
        }

        @Override
        public Handler<Throwable> exceptionHandler() {
            return delegate.exceptionHandler();
        }

        @Override
        public String toString() {
            return "ContextWrapper(" + delegate + ")";
        }

        @Override
        public Executor executor() {
            return cinternal().executor();
        }

        @Override
        public EventLoop nettyEventLoop() {
            return cinternal().nettyEventLoop();
        }

        @Override
        public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
            return cinternal().executeBlocking(blockingCodeHandler, queue);
        }

        @Override
        public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
            return cinternal().executeBlockingInternal(action);
        }

        @Override
        public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
            return cinternal().executeBlocking(action, ordered);
        }

        @Override
        public Deployment getDeployment() {
            return cinternal().getDeployment();
        }

        @Override
        public boolean inThread() {
            return cinternal().inThread();
        }

        @Override
        public <T> void emit(T argument, Handler<T> task) {
            cinternal().emit(argument, task);
        }

        @Override
        public void execute(Runnable task) {
            cinternal().execute(scope.wrap(task));
        }

        @Override
        public <T> void execute(T argument, Handler<T> task) {
            cinternal().execute(argument, scope.wrapGeneral(task));
        }

        @Override
        public void reportException(Throwable t) {
            cinternal().reportException(t);
        }

        @Override
        public ConcurrentMap<Object, Object> contextData() {
            return cinternal().contextData();
        }

        @Override
        public ConcurrentMap<Object, Object> localContextData() {
            return cinternal().localContextData();
        }

        @Override
        public ClassLoader classLoader() {
            return cinternal().classLoader();
        }

        @Override
        public WorkerPool workerPool() {
            return cinternal().workerPool();
        }

        @Override
        public VertxTracer tracer() {
            return cinternal().tracer();
        }

        @Override
        public ContextInternal duplicate() {
            return new ContextWrapper(cinternal().duplicate());
        }

        @Override
        public CloseFuture closeFuture() {
            return cinternal().closeFuture();
        }
    }
}
