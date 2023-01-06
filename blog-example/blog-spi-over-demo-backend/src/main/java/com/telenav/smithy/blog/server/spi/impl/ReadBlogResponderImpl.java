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
package com.telenav.smithy.blog.server.spi.impl;

import com.telenav.smithy.blog.demo.data.BlogStore;
import com.google.inject.Inject;
import com.telenav.smithy.http.HeaderTypes;
import static com.telenav.smithy.http.HeaderTypes.headerTypes;
import com.telenav.smithy.http.ResponseException;
import com.telenav.smithy.http.SmithyRequest;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.blog.model.ReadBlogInput;
import com.telenav.blog.model.ReadBlogOutput;
import com.telenav.blog.spi.ReadBlogResponder;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class ReadBlogResponderImpl implements ReadBlogResponder {

    private final BlogStore store;

    @Inject
    ReadBlogResponderImpl(BlogStore store) {
        this.store = store;
    }

    @Override
    public void respond(SmithyRequest request, ReadBlogInput input,
            SmithyResponse<ReadBlogOutput> output) throws Exception {
        Optional<ReadBlogOutput> opt = store.blog(input.id());
        if (!opt.isPresent()) {
            output.completeExceptionally(new ResponseException(410, input.id().toString()));
            return;
        }
        String hash = store.blogHash(input.id());

        ReadBlogOutput blog = opt.get();
        boolean matched = isEtagMatch(hash, request) || isLastModifiedMatch(blog, request);

        if (matched) {
            output.status(304).complete(null);
            return;
        }

        blog.metadata().lastModified().ifPresent(lm -> {
            output.add(headerTypes().lastModified(), lm);
        });
        output.add(headerTypes().etag(), hash);

        if (request.isMethod("HEAD")) {
            output.complete(null);
        } else {
            output.complete(blog);
        }
    }

    private static boolean isEtagMatch(String hash, SmithyRequest request) {
        return request.header(HeaderTypes.headerTypes().ifNoneMatch()).map(inm -> {
            return inm.toString().equals(hash);
        }).orElse(false);
    }

    private static boolean isLastModifiedMatch(ReadBlogOutput blog, SmithyRequest request) {
        return request.header(HeaderTypes.headerTypes().ifModifiedSince()).map(ims -> {
            Instant when = blog.metadata().lastModified().orElse(blog.metadata().created())
                    .with(ChronoField.MILLI_OF_SECOND, 0);
            return when.equals(ims) || when.isAfter(ims);
        }).orElse(false);
    }

}
