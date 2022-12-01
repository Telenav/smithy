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
import com.mastfrog.smithy.http.InvalidInputException;
import com.mastfrog.smithy.http.ResponseException;
import com.telenav.blog.BlogService;
import com.telenav.vertx.guice.VertxGuiceModule;
import com.telenav.vertx.guice.verticle.VerticleBuilder;
import io.vertx.ext.web.RoutingContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Tim Boudreau
 */
public class BlogDemoVertx {

    public static void main(String[] args) {
        new BlogService()
                .withReadBlogResponder(ReadBlogResponderImpl.class)
                .authenticateWithAuthUserUsing(AuthImpl.class)
                .withListBlogsResponder(ListBlogsResponderImpl.class)
                .withListCommentsResponder(ListCommentsResponderImpl.class)
                .configuringVertxWith(BlogDemoVertx::configureVertx)
                .configuringVerticleWith(BlogDemoVertx::configureVerticle)
                .installing(binder -> {
                    binder.bind(ExecutorService.class)
                            .annotatedWith(Names.named("background"))
                            .toInstance(Executors.newCachedThreadPool());

                    Path dir = Paths.get("/tmp/blog-demo");
                    binder.bind(Path.class).annotatedWith(Names.named("blogDir")).toInstance(dir);
                })
                .start(8123);
    }

    static void configureVertx(VertxGuiceModule mod) {
        mod.withVertxOptionsCustomizer(vx -> {
            // We have a single verticle - there is no point, only overhead,
            // in using elaborate classloading strategies
            vx.setDisableTCCL(true);
            return vx;
        });
    }

    static <T> VerticleBuilder<T> configureVerticle(VerticleBuilder<T> vb) {
        vb.customizingRouterWith(rtr -> {
            rtr.errorHandler(500, (RoutingContext res) -> {
                if (res.failure() != null) {
                    System.out.println("HAVE FAILURE " + res.failure());
                    if (res.failure() instanceof IllegalArgumentException
                            || res.failure() instanceof DateTimeParseException
                            || res.failure() instanceof InvalidInputException) {
                        String msg = res.failure().getMessage();
                        if (msg == null) {
                            res.response().setStatusCode(400).send();
                        } else {
                            res.response().setStatusCode(400)
                                    .send(msg);
                        }
                    } else if (res.failure() instanceof ResponseException) {
                        ResponseException ex = (ResponseException) res.failure();
                        res.response().setStatusCode(ex.status())
                                .send(ex.getMessage());
                    } else if (res.failure() instanceof UnsupportedOperationException) {
                        res.response().setStatusCode(501)
                                .send(res.failure().getMessage());
                    }
                }
            });
            return rtr;
        });
        return vb;
    }
}
