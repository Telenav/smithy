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
package com.telenav.smithy.blog.demo.vertx;

import com.telenav.smithy.blog.server.spi.impl.ListBlogsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ListCommentsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.AuthImpl;
import com.telenav.smithy.blog.server.spi.impl.ReadBlogResponderImpl;
import com.google.inject.name.Names;
import com.mastfrog.util.strings.Strings;
import com.telenav.blog.BlogService;
import com.telenav.blog.BlogServiceOperations;
import com.telenav.smithy.blog.server.spi.impl.HealthResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.LoginResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.NewBlogResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.PingResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.PutCommentResponderImpl;
import com.telenav.smithy.vertx.probe.ProbeImplementation;
import com.telenav.vertx.guice.VertxGuiceModule;
import com.telenav.vertx.guice.verticle.VerticleBuilder;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.web.RoutingContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedSet;
import java.util.Set;
import java.util.WeakHashMap;

/**
 *
 * @author Tim Boudreau
 */
public class BlogDemoVertx {

    static {
        System.setProperty("vertx.logger-delegate-factory-class-name", VertxLogging.class.getName());
//        Logger routingLogger = (Logger) LoggerFactory.getLogger(io.vertx.ext.web.RoutingContext.class);
//        routingLogger.setLevel(Level.DEBUG);
    }

    public static void main(String[] args) {
        new BlogService()
                .withReadBlogResponderType(ReadBlogResponderImpl.class)
                .authenticateWithAuthUserUsing(AuthImpl.class)
                .withListBlogsResponderType(ListBlogsResponderImpl.class)
                .withListCommentsResponderType(ListCommentsResponderImpl.class)
                .withNewBlogResponderType(NewBlogResponderImpl.class)
                .withPingResponderType(PingResponderImpl.class)
                .withHealthResponderType(HealthResponderImpl.class)
                .withPutCommentResponderType(PutCommentResponderImpl.class)
                .withLoginResponderType(LoginResponderImpl.class)
                .configuringVertxWith(BlogDemoVertx::configureVertx)
                .configuringVerticleWith(BlogDemoVertx::configureVerticleBuilder)
                .withProbe(ProbeImplementation.stderr())
                .withProbe(new P())
                .asyncProbe()
                .withModule(binder -> {
                    binder.bind(Path.class)
                            .annotatedWith(Names.named("blogDir"))
                            .toInstance(Paths.get("/tmp/blog-demo"));
                })
                .start(8123);
    }

    static void configureVerticleBuilder(VerticleBuilder<?> vb) {
        vb.customizingHttpOptionsWith(opts
                -> opts.setReuseAddress(false)
                        .setReusePort(false)
                        .setTcpFastOpen(true)
                        .setTcpNoDelay(true)
                        .setTcpCork(false)
                        .setLogActivity(true)
//                        .setCompressionSupported(true)
                        .setDecoderInitialBufferSize(32768)
//                        .setHandle100ContinueAutomatically(false)
                        .setLogActivity(true)
//                        .setTracingPolicy(TracingPolicy.ALWAYS)
        );
        vb.customizingRouterWith(r -> {
            return r.errorHandler(-1, rc -> {
                System.out.println("Request dropped on floor? " + rc.request().uri());
                HttpServerResponse resp = rc.response();
                resp.setStatusCode(501);
                resp.end(Strings.toString(new Exception(rc.request().uri())));
            });
        });
    }

    static void configureVertx(VertxGuiceModule mod) {
        EventBusOptions opts = new EventBusOptions()
                .setLogActivity(true)
                .setConnectTimeout(5000);
        mod.withVertxOptionsCustomizer(vx -> {
            // We have a single verticle - there is no point, only overhead,
            // in using elaborate classloading strategies
            vx.setDisableTCCL(true)
                    .setHAEnabled(false)
                    .setEventBusOptions(opts);
            return vx;
        });
    }

    static class P implements ProbeImplementation<BlogServiceOperations> {

        private static final Set<RoutingContext> listeningTo = synchronizedSet(newSetFromMap(new WeakHashMap<>()));

        @Override
        public void onEnterHandler(BlogServiceOperations op, RoutingContext event, Class<? extends Handler<RoutingContext>> handler) {
            System.out.println("P onEnter " + op + " " + handler.getSimpleName() + " " + event.request().uri());
            if (listeningTo.add(event)) {
                event.addEndHandler(end -> {
                    System.out.println(op + " succeeded? " + end.succeeded() + " failed? " + end.failed());
                    Throwable cause = end.cause();
                    if (cause != null) {
                        cause.printStackTrace();
                    } else {
                        System.out.println("No cause - end with " + end);
                    }
                });
            }
        }

    }

}
