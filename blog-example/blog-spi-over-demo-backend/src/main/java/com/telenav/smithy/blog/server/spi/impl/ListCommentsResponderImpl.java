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
import com.telenav.smithy.http.ResponseException;
import com.telenav.smithy.http.SmithyRequest;
import com.telenav.smithy.http.SmithyResponse;
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
        store.comments(input.id(), input.approved() && authInfo.isPresent())
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
