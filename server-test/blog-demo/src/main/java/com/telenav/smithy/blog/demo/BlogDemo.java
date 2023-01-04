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
package com.telenav.smithy.blog.demo;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.jackson.JacksonModule;
import static com.mastfrog.jackson.configuration.TimeSerializationMode.TIME_AS_ISO_STRING;
import static com.mastfrog.jackson.configuration.DurationSerializationMode.DURATION_AS_ISO_STRING;
import com.telenav.blog.BlogService;
import com.telenav.smithy.blog.demo.data.BlogStore;
import com.telenav.smithy.blog.server.spi.impl.AuthImpl;
import com.telenav.smithy.blog.server.spi.impl.HealthResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ListBlogsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ListCommentsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.LoginResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.NewBlogResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.PingResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.PutCommentResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ReadBlogResponderImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author Tim Boudreau
 */
public class BlogDemo extends AbstractModule {

    public static void main(String[] args) throws Exception {
        System.setProperty("acteur.debug", "true");
        new BlogService()
                .withModule(new BlogDemo())
                .withModule(new JacksonModule()
                        .withJavaTimeSerializationMode(TIME_AS_ISO_STRING, DURATION_AS_ISO_STRING))
                .withAuthenticatorForAuthenticateWithAuthUser(AuthImpl.class)
                .withListBlogsResponderType(ListBlogsResponderImpl.class)
                .withReadBlogResponderType(ReadBlogResponderImpl.class)
                .withListCommentsResponderType(ListCommentsResponderImpl.class)
                .withNewBlogResponderType(NewBlogResponderImpl.class)
                .withPutCommentResponderType(PutCommentResponderImpl.class)
                .withPingResponderType(PingResponderImpl.class)
                .withHealthResponderType(HealthResponderImpl.class)
                .withLoginResponderType(LoginResponderImpl.class)
                .mappingExceptionTo(BlogStore.BlogAlreadyExistsException.class, HttpResponseStatus.CONFLICT)
                .mappingExceptionTo(BlogStore.CommentAlreadyExistsException.class, HttpResponseStatus.CONFLICT)
                .mappingExceptionTo(BlogStore.NoSuchBlogException.class, HttpResponseStatus.GONE)
                .start(args)
                .await();
    }

    @Override
    protected void configure() {
        Path dir = Paths.get("/tmp/blog-demo");
        bind(Path.class).annotatedWith(Names.named("blogDir")).toInstance(dir);
    }
}
