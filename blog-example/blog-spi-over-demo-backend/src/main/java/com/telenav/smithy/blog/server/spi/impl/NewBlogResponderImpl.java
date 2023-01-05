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

import com.google.inject.Inject;
import com.mastfrog.smithy.http.SmithyRequest;
import com.mastfrog.smithy.http.SmithyResponse;
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
