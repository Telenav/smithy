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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telenav.smithy.blog.demo.data.BlogStore;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.acteur.header.entities.CacheControl;
import static com.mastfrog.smithy.http.HeaderTypes.headerTypes;
import com.mastfrog.smithy.http.SmithyRequest;
import com.mastfrog.smithy.http.SmithyResponse;
import com.telenav.blog.model.AuthUser;
import com.telenav.blog.model.BlogInfo;
import com.telenav.blog.model.BlogList;
import com.telenav.blog.model.ListBlogsInput;
import com.telenav.blog.model.ListBlogsOutput;
import com.telenav.blog.spi.ListBlogsResponder;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author Tim Boudreau
 */
public final class ListBlogsResponderImpl implements ListBlogsResponder {

    private final BlogStore blogs;
    private final ObjectMapper mapper;

    @Inject
    ListBlogsResponderImpl(BlogStore blogs,
            @Named("background") ExecutorService svc,
            ObjectMapper mapper) {
        this.blogs = blogs;
        this.mapper = mapper;
    }

    @Override
    public void respond(SmithyRequest request, Optional<AuthUser> authInfo,
            ListBlogsInput input, SmithyResponse<ListBlogsOutput> output) throws Exception {

        System.out.println("INPUT:\n\n" + mapper.writeValueAsString(input) + "\n\n");

        BlogList blogList = blogs.list();
        if (input.since().isPresent() || input.tags().isPresent() || input.search().isPresent() || input.count().isPresent()) {
            blogList = new BlogList(blogList.filter(blog -> {
                if (input.tags().isPresent()) {
                    if (!blog.metadata().tags().isPresent()) {
                        return false;
                    }
                    if (blog.metadata().tags().get().filter(tag -> input.tags().get().contains(tag)).isEmpty()) {
                        return false;
                    }
                }
                if (input.since().isPresent()) {
                    if (blog.metadata().created().isBefore(input.since().get())) {
                        return false;
                    }
                }
                return true;
            }));
        }
        if (input.count().isPresent()) {
            int ct = input.count().get();
            if (blogList.size() > ct) {
                blogList = new BlogList(blogList.subList(0, ct));
            }
        }
        long hash = 0;
        Instant mostRecentLastModified = null;
        for (BlogInfo item : blogList) {
            Instant lm = item.metadata().lastModified()
                    .or(() -> Optional.of(item.metadata().created())).orElseThrow();
            if (mostRecentLastModified == null) {
                mostRecentLastModified = lm;
            } else {
                if (lm.compareTo(mostRecentLastModified) > 0) {
                    mostRecentLastModified = lm;
                }
            }

            hash += item.hashCode() + item.metadata().lastModified().get().hashCode();
        }
        String et = Long.toString(hash, 36);
        output.add(headerTypes().etag(), et);

        Optional<String> inboundEtag = input.etag();
        if (inboundEtag.isPresent()) {
            if (inboundEtag.get().toString().equals(et)) {
                if (mostRecentLastModified != null) {
                    output.add(headerTypes().lastModified(), mostRecentLastModified);
                }
                output.status(304).complete(null);
                return;
            }
        }

        if (mostRecentLastModified != null) {
            output.add(headerTypes().lastModified(), mostRecentLastModified);
            Optional<Instant> inboundLastModified = request.header(headerTypes().ifModifiedSince());
            if (inboundLastModified.isPresent()) {
                if (inboundLastModified.get().isAfter(mostRecentLastModified)) {
                    output.status(304).complete(null);
                    return;
                }
            }
        }

        output.add(headerTypes().cacheControl(), CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY);

        blogs.mostRecentLastModified().ifPresent(newest -> {
            output.add(headerTypes().lastModified(), newest);
        });

        if (request.isMethod("HEAD")) {
            output.complete(null);
        } else {
            output.complete(new ListBlogsOutput(blogList));
        }
    }

}
