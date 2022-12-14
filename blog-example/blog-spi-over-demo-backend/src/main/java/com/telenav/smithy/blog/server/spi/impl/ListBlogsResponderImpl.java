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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telenav.smithy.blog.demo.data.BlogStore;
import com.google.inject.Inject;
import com.mastfrog.acteur.header.entities.CacheControl;
import static com.telenav.smithy.http.HeaderTypes.headerTypes;
import com.telenav.smithy.http.SmithyRequest;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.blog.model.AuthUser;
import com.telenav.blog.model.BlogInfo;
import com.telenav.blog.model.BlogList;
import com.telenav.blog.model.ListBlogsInput;
import com.telenav.blog.model.ListBlogsOutput;
import com.telenav.blog.spi.ListBlogsResponder;
import java.time.Instant;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class ListBlogsResponderImpl implements ListBlogsResponder {

    private final BlogStore blogs;
    private final ObjectMapper mapper;

    @Inject
    ListBlogsResponderImpl(BlogStore blogs,
            ObjectMapper mapper) {
        this.blogs = blogs;
        this.mapper = mapper;
    }

    @Override
    public void respond(SmithyRequest request, Optional<AuthUser> authInfo,
            ListBlogsInput input, SmithyResponse<ListBlogsOutput> output) throws Exception {

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
