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

import com.google.inject.Inject;
import com.telenav.smithy.http.SmithyRequest;
import com.telenav.smithy.http.SmithyResponse;
import com.telenav.blog.model.AuthUser;
import com.telenav.blog.model.BlogId;
import com.telenav.blog.model.NewBlogInput;
import com.telenav.blog.model.NewBlogOutput;
import com.telenav.blog.spi.NewBlogResponder;
import com.telenav.smithy.blog.demo.data.BlogStore;

/**
 *
 * @author Tim Boudreau
 */
public class NewBlogResponderImpl implements NewBlogResponder {

    private final BlogStore store;

    @Inject
    public NewBlogResponderImpl(BlogStore store) {
        this.store = store;
    }

    @Override
    public void respond(SmithyRequest request, AuthUser authInfo,
            NewBlogInput input, SmithyResponse<NewBlogOutput> output) throws Exception {
        
        System.out.println("New Blog: " + input);
        
        // XXX deleteme once cors handling is fixed
        output.add("access-control-allow-origin", "*");
        output.add("access-control-allow-methods", "GET,POST,PUT,OPTIONS");
        output.add("access-control-allow-headers", "accept,authorization,content-type,x-requested-with,if-match,if-unmodified-since");
        output.add("access-control-allow-credentials", "true");
        output.add("access-control-allow-max-age", "600");
        
        BlogId newId = store.newBlog(input);
        output.complete(new NewBlogOutput(newId));
        
        System.out.println("Completed output " + newId);
    }

}
