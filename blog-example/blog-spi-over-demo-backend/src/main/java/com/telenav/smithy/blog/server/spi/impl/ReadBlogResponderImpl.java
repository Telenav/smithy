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
package com.telenav.smithy.blog.server.spi.impl;

import com.telenav.smithy.blog.demo.data.BlogStore;
import com.google.inject.Inject;
import com.mastfrog.smithy.http.HeaderTypes;
import static com.mastfrog.smithy.http.HeaderTypes.headerTypes;
import com.mastfrog.smithy.http.ResponseException;
import com.mastfrog.smithy.http.SmithyRequest;
import com.mastfrog.smithy.http.SmithyResponse;
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
