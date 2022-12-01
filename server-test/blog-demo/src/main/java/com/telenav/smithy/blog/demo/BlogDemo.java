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

import static com.google.common.base.Charsets.UTF_8;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.jackson.JacksonModule;
import static com.mastfrog.jackson.configuration.TimeSerializationMode.TIME_AS_ISO_STRING;
import static com.mastfrog.jackson.configuration.DurationSerializationMode.DURATION_AS_ISO_STRING;
import com.telenav.blog.BlogService;
import com.telenav.blog.auth.AuthenticateWithAuthUser;
import com.telenav.smithy.blog.server.spi.impl.AuthImpl;
import com.telenav.smithy.blog.server.spi.impl.ListBlogsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ListCommentsResponderImpl;
import com.telenav.smithy.blog.server.spi.impl.ReadBlogResponderImpl;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URLDecoder;
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
                .withListBlogsResponderType(ListBlogsResponderImpl.class)
                .withReadBlogResponderType(ReadBlogResponderImpl.class)
                .withListCommentsResponderType(ListCommentsResponderImpl.class)
                .start(args)
                .await();
    }

    @Override
    protected void configure() {
        Path dir = Paths.get(System.getProperty("user.home")).resolve("work/personal/blog-data");
        bind(Path.class).annotatedWith(Names.named("blogDir")).toInstance(dir);
        bind(AuthenticateWithAuthUser.class).to(AuthImpl.class);
        bind(RequestLogger.class).to(RL.class).asEagerSingleton();
    }
    
    static class RL implements RequestLogger {

        @Override
        public void onBeforeEvent(RequestID rid, Event<?> event) {
            HttpEvent e = (HttpEvent) event;
            System.out.println("\n-------- REQUEST " + e.getRequestURL(false));
            System.out.println("URI: " + URLDecoder.decode(e.requestUri(), UTF_8));
            System.out.println(e.path());
            e.httpHeaderNames().forEach(hdr -> {
                System.out.println("  " + hdr + ":\t" + e.header(hdr));
            });
        }
        
        @Override
        public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
            System.out.println(rid + ": " + event + ": " + status);
        }
        
    }

}
