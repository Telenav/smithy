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
package com.telenav.vertx.guice;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.http.harness.HttpTestHarness;
import com.mastfrog.util.net.PortFinder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class VertxGuiceModuleTest {

    private static ThrowingRunnable onShutdown;
    private static final PortFinder PORTS = new PortFinder();
    static HttpTestHarness<String> harn1;
    static HttpTestHarness<String> harn2;
    static String rndString;
    static int port_a = -1;
    static int port_b = -1;

    @Test
    public void testSimpleRequest() {
        harn1.get("goodbye").test(assertions -> {
            assertions
                    .assertOk()
                    .assertBody("goodbye");
        }).assertNoFailures();
    }

    @Test
    public void testRequestingInjectedValue() {
        harn1.get("hello").test(assertions -> {
            assertions
                    .assertOk()
                    .assertBody("Hello, " + rndString);
        }).assertNoFailures();
    }

    @Test
    public void testSecondVerticleWithInjectedValue() {
        harn2.get("whatever").test(assertions -> {
            assertions
                    .assertOk()
                    .assertBody("Whazzup, " + rndString);
        }).assertNoFailures();

        harn2.get("stuff?name=you").test(assertions -> {
            assertions
                    .assertOk()
                    .assertHeaderEquals("ETag", "whatevs")
                    .assertBody("Whazzup, you");
        }).assertNoFailures();
    }

    @Test
    public void testMultipleLazilyInstantiatedHandlers() {
        harn1.get("multi").test(assertions -> {
            assertions
                    .assertOk()
                    .assertBody("1,2,3")
                    .assertHeaderEquals("x-one", "1")
                    .assertHeaderEquals("x-two", "2")
                    .assertHeaderEquals("x-three", "3");
        }).assertNoFailures();
    }

    @BeforeAll
    @SuppressWarnings("ThrowableResultIgnored")
    public static void setUpClass() {
        onShutdown = ThrowingRunnable.oneShot(true);
        rndString = "Mr. " + Long.toString(ThreadLocalRandom.current().nextLong(), 36);
        port_a = PORTS.findAvailableServerPort();
        port_b = PORTS.findAvailableServerPort();
        VertxGuiceModule mod = new VertxGuiceModule()
                .withModule(
                        (Binder bnd) -> {
                            bnd.bind(String.class)
                                    .annotatedWith(named("who")).toInstance(rndString);
                            bnd.bind(Integer.class)
                                    .annotatedWith(named("threads")).toInstance(3);
                        })
                .withVerticle()
                .customizingHttpOptionsWith(opts -> {
                    return opts.setPort(port_a)
                            .setHost("localhost");
                })
                .withPort(port_a)
                .customizingRouterWith(rt -> {
                    return rt.allowForward(AllowForwardHeaders.ALL);
                })
                .route().forHttpMethod(HttpMethod.GET)
                .withPath("/hello")
                .handledBy(MyHandler.class)
                .route().forHttpMethod(HttpMethod.GET)
                .withPath("/goodbye")
                .handledBy(x -> {
                    x.end("goodbye");
                })
                .route().forHttpMethod(HttpMethod.GET)
                .withPath("/multi")
                .withHandler(MultiHandlerOne.class)
                .withHandler(MultiHandlerTwo.class)
                .withHandler(MultiHandlerThree.class)
                .terminatedBy(MultiHandlerEnd.class)
                .bind()
                .withVerticle(CustomVerticle.class);

        VertxLauncher launcher = Guice.createInjector(mod).getInstance(VertxLauncher.class);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        launcher.start(futures -> {
            assertTrue(futures.size() > 1);
            CountDownLatch latch = new CountDownLatch(futures.size());
            for (Future<String> fut : futures) {
                fut.andThen(result -> {
                    try {
                        if (result.cause() != null) {
                            thrown.getAndUpdate(old -> {
                                if (old != null) {
                                    old.addSuppressed(result.cause());
                                    return old;
                                }
                                return fut.cause();
                            });
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Logger.getLogger(VertxGuiceModuleTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        onShutdown.andAlways(launcher::shutdown);

        harn1 = HttpTestHarness.builder().withHttpVersion(HttpClient.Version.HTTP_1_1)
                .build().convertingToUrisWith((String path) -> {
                    return URI.create("http://localhost:" + port_a + "/" + path);
                });
        onShutdown.andAlways(harn1::shutdown);
        harn2 = HttpTestHarness.builder().withHttpVersion(HttpClient.Version.HTTP_1_1)
                .build().convertingToUrisWith((String path) -> {
                    return URI.create("http://localhost:" + port_b + "/" + path);
                });
        onShutdown.andAlways(harn2::shutdown);
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
        onShutdown.run();
    }

    static class DeploymentOptionsCustomizer implements UnaryOperator<DeploymentOptions> {

        private final int threads;

        @Inject
        public DeploymentOptionsCustomizer(@Named("threads") int threads) {
            this.threads = threads;
        }

        @Override
        public DeploymentOptions apply(DeploymentOptions t) {
            return t.setWorkerPoolSize(threads);
        }
    }

    static class MyHandler implements Handler<RoutingContext> {

        private final String who;

        @Inject
        MyHandler(@Named("who") String who) {
            this.who = who;
        }

        @Override
        public void handle(RoutingContext e) {
            e.end("Hello, " + who);
        }
    }

    static class MultiHandlerOne implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext event) {
            event.response().putHeader("x-one", "1");
            event.vertx().nettyEventLoopGroup().submit(event::next);
        }

    }

    static class MultiHandlerTwo implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext event) {
            event.response().putHeader("x-two", "2");
            event.vertx().nettyEventLoopGroup().submit(event::next);
        }

    }

    static class MultiHandlerThree implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext event) {
            event.response().putHeader("x-three", "3");
            event.vertx().nettyEventLoopGroup().submit(event::next);
        }

    }

    static class MultiHandlerEnd implements Handler<RoutingContext> {

        @Override
        public void handle(RoutingContext event) {
            StringBuilder sb = new StringBuilder();
            HttpServerResponse resp = event.response();
            MultiMap hdrs = resp.headers();
            for (String s : new String[]{"x-one", "x-two", "x-three"}) {
                if (sb.length() > 0) {
                    sb.append(",");
                }
                sb.append(hdrs.get(s));
            }
            event.response().putHeader("x-done", "true");
            resp.send(sb.toString());
        }

    }

    static class CustomVerticle extends AbstractVerticle {

        private final Provider<Vertx> vertx;
        private final String who;

        @Inject
        CustomVerticle(Provider<Vertx> vertx, @Named("who") String who) {
            this.vertx = vertx;
            this.who = who;
        }

        @Override
        public void start() throws Exception {
            Router router = Router.router(vertx.get());

            // Mount the handler for all incoming requests at every path and HTTP method
            router.route().handler((RoutingContext context) -> {

                MultiMap queryParams = context.queryParams();
                String name = queryParams.contains("name") ? queryParams.get("name") : who;

                HttpServerResponse resp = context.response();
                resp.putHeader("ETag", "whatevs");
                resp.send("Whazzup, " + name);
//                context.end("Whazzup, " + name);
            });

            // Create the HTTP server
            vertx.get().createHttpServer()
                    // Handle every request using the router
                    .requestHandler(router)
                    // Start listening
                    .listen(port_b)
                    // Print the port
                    .onSuccess(server
                            -> System.out.println(
                            "Second HTTP server started on port " + server.actualPort()
                    ));
        }
    }
}
