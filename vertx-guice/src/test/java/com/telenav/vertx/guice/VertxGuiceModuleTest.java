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
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.AllowForwardHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterAll;
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
                    .assertBody("Whazzup, you");
        }).assertNoFailures();
    }

    @BeforeAll
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
                .bind()
                .withVerticle(CustomVerticle.class);

        VertxLauncher launcher = Guice.createInjector(mod).getInstance(VertxLauncher.class);
        launcher.start();
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

                context.end("Whazzup, " + name);
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
