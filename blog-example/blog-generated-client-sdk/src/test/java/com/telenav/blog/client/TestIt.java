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
package com.telenav.blog.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.mastfrog.smithy.client.result.ServiceResult;
import com.telenav.blog.model.ListBlogsInput;
import com.telenav.blog.model.ListBlogsOutput;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 *
 * @author Tim Boudreau
 */
public class TestIt {

    public static void main(String[] args) throws InterruptedException, ExecutionException, JsonProcessingException {
        BlogServiceClient cli = new BlogServiceClient();

        ListBlogsInput input = ListBlogsInput.builder()
                .withCount(10)
                //                .withTags(new Tags("java"))
                //                .withEtag("blah")
                //                .withSearch("java")
                //                .withSince(Instant.EPOCH)
                //                //                        .withEtag("-usmcos")
                //                .withIfModifiedSince(Instant.EPOCH)
                .build();

        CompletableFuture<ServiceResult<ListBlogsOutput>> blogs
                = cli.listBlogs(input);

        ServiceResult<ListBlogsOutput> res = blogs.get();
        System.out.println("Result status " + res.reason());

        res.info().ifPresent(info -> {
            System.out.println("STATUS " + info.statusCode());
            String lm = info.headers().firstValue("last-modified").orElse("");
            String etag = info.headers().firstValue("etag").orElse("");
            System.out.println("Last-Modified: " + lm);
            System.out.println("Etag: " + etag);
        });

        if (res.result().isPresent()) {
            res.result().get().blogs().forEach(blog -> {
                System.out.println(blog.metadata().created() + " " + blog.title()
                        + "\t" + blog.metadata().tags().map(Object::toString).orElse(""));
                System.out.println("-----------------------");
            });
        }
    }
}
