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
import com.telenav.blog.BlogService;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author Tim Boudreau
 */
public class BlogDemoVertx {

    public static void main(String[] args) {
        new BlogService()
                .authenticateWithAuthUserUsing(AuthImpl.class)
                .withListBlogsResponder(ListBlogsResponderImpl.class)
                .withListCommentsResponder(ListCommentsResponderImpl.class)
                .withReadBlogResponder(ReadBlogResponderImpl.class)
                .installing(binder -> {
                    binder.bind(ExecutorService.class)
                            .annotatedWith(Names.named("background"))
                            .toInstance(Executors.newCachedThreadPool());

                    Path dir = Paths.get("/tmp/blog-demo");
                    binder.bind(Path.class).annotatedWith(Names.named("blogDir")).toInstance(dir);
                })
                .start(8123);
    }
}
