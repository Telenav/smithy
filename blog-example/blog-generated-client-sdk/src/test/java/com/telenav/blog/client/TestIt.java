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
package com.telenav.blog.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.telenav.smithy.client.result.ServiceResult;
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
