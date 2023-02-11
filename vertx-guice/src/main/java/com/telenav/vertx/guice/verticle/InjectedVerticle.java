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
package com.telenav.vertx.guice.verticle;

import com.google.inject.Provider;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public final class InjectedVerticle extends AbstractVerticle {

    private final Provider<Router> routerProvider;
    private final Provider<HttpServerOptions> serverOptionsCustomizer;
    private final Provider<Vertx> vertxProvider;
    private final int port;
    private final List<RouteCreationHandler> routeCreators;

    @Inject
    InjectedVerticle(Provider<Router> router,
            Provider<HttpServerOptions> serverOptionsCustomizer,
            Provider<Vertx> vertx, List<RouteCreationHandler> routeCreators,
            VerticleInfo info) {
        this.routerProvider = router;
        port = info.port;
        this.serverOptionsCustomizer = serverOptionsCustomizer;
        this.vertxProvider = vertx;
        this.routeCreators = routeCreators;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        launch().onComplete(res -> {
            if (res.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail(res.cause());
            }
        });
    }

    private Future<HttpServer> launch() {
        Router router = this.routerProvider.get();
        routeCreators.forEach(creator -> {
            creator.applyTo(router);
        });
        Vertx vx = this.vertxProvider.get();
        return vx.createHttpServer(serverOptionsCustomizer.get())
                .requestHandler(router)
                .listen(port);
    }

    @Override
    public void start() throws Exception {
        // THe default vertx behavior is to block-hole failures,
        // so jump through some hoops to avoid it.
        CountDownLatch latch = new CountDownLatch(1);
        Future<HttpServer> result = launch();
        result.onComplete(serverResult -> {
            try {
                if (serverResult.succeeded()) {
                    System.out.println("Server started on port "
                            + serverResult.result().actualPort());
                }
            } finally {
                latch.countDown();
            }
        });
        Throwable th = null;
        if (!latch.await(30, SECONDS) && !result.isComplete()) {
            th = new TimeoutException("Timedout waiting for socket open");
        } else if (result.failed()) {
            if (result.cause() != null) {
                th = result.cause();
            } else {
                th = new Exception("Unspecified failures");
            }
        }
        if (th instanceof Exception) {
            throw (Exception) th;
        } else if (th instanceof Error) {
            throw (Error) th;
        } else if (th != null) { // ThreadDeath?
            throw new Error(th);
        }
        HttpServer server = result.result();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                CountDownLatch shutdownLatch = new CountDownLatch(1);
                server.close(closeResult -> {
                    try {
                        if (closeResult.cause() != null) {
                            closeResult.cause().printStackTrace();
                        }
                    } finally {
                        shutdownLatch.countDown();
                    }
                });
                shutdownLatch.await();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }, "Shutdown http"));
    }
}
