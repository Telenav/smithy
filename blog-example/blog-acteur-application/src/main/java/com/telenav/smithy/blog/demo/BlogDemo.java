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
