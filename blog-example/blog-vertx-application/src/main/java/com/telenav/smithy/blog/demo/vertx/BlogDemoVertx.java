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
package com.telenav.smithy.blog.demo.vertx;

import com.telenav.smithy.blog.server.spi.impl.ListBlogsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ListCommentsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.AuthImpl;
import com.telenav.smithy.blog.server.spi.impl.ReadBlogResponderImpl;
import com.google.inject.name.Names;
import com.mastfrog.util.strings.Strings;
import com.telenav.blog.BlogService;
import com.telenav.blog.spi.BlogServiceOperations;
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
        System.setProperty("vertx.logger-delegate-factory-class-name", VertxJULLogging.class.getName());
//        System.setProperty("vertx.logger-delegate-factory-class-name", VertxLogging.class.getName());
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
                -> opts.setReuseAddress(true)
                        .setReusePort(true)
                        .setSoLinger(1)
//                        .setTcpFastOpen(true)
//                        .setTcpNoDelay(true)
//                        .setTcpCork(false)
                        .setLogActivity(true)
                        .setCompressionSupported(true)
                        .setDecoderInitialBufferSize(32768)
//                        .setHandle100ContinueAutomatically(false)
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
