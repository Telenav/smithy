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
import com.mastfrog.smithy.http.ResponseException;
import com.mastfrog.smithy.http.SmithyRequest;
import com.mastfrog.smithy.http.SmithyResponse;
import com.telenav.blog.model.AuthUser;
import com.telenav.blog.model.ListCommentsInput;
import com.telenav.blog.model.ListCommentsOutput;
import com.telenav.blog.spi.ListCommentsResponder;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public final class ListCommentsResponderImpl implements ListCommentsResponder {

    private final BlogStore store;

    @Inject
    ListCommentsResponderImpl(BlogStore store) {
        this.store = store;
    }

    @Override
    public void respond(SmithyRequest request, Optional<AuthUser> authInfo,
            ListCommentsInput input, SmithyResponse<ListCommentsOutput> output)
            throws Exception {

        if (!input.approved() && !authInfo.isPresent()) {
            output.completeExceptionally(new ResponseException(403,
                    "Only admins can view unpublished comments"));
        }
        store.comments(input.id(), input.approved())
                .ifPresentOrElse(comments -> {
                    if (request.isMethod("HEAD")) {
                        output.complete(null);
                    } else {
                        output.complete(comments);
                    }
                }, () -> {
                    output.completeExceptionally(new ResponseException(410, "No such blog"));
                });
    }

}
