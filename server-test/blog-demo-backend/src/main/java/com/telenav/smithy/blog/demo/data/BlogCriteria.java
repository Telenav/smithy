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
package com.telenav.smithy.blog.demo.data;

import com.telenav.blog.model.ReadBlogOutput;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
public class BlogCriteria implements Predicate<ReadBlogOutput> {

    private boolean published = true;
    private Predicate<? super ReadBlogOutput> test = null;

    public static BlogCriteria blogCriteria() {
        return new BlogCriteria();
    }

    public static BlogCriteria unpublishedBlogCriteria() {
        return new BlogCriteria().unpublished();
    }

    public static BlogCriteria blogCriteria(boolean published) {
        BlogCriteria result = blogCriteria();
        result.published = published;
        return result;
    }

    public BlogCriteria unpublished() {
        published = false;
        return this;
    }

    public BlogCriteria published() {
        published = true;
        return this;
    }

    public BlogCriteria with(Predicate<? super ReadBlogOutput> p) {
        this.test = p;
        return this;
    }

    public BlogCriteria or(Predicate<? super ReadBlogOutput> p) {
        if (test == null) {
            test = p;
        } else {
            Predicate<? super ReadBlogOutput> old = test;
            test = o -> old.test(o) || p.test(o);
        }
        return this;
    }

    public BlogCriteria and(Predicate<? super ReadBlogOutput> p) {
        if (test == null) {
            test = p;
        } else {
            Predicate<? super ReadBlogOutput> old = test;
            test = o -> old.test(o) && p.test(o);
        }
        return this;
    }

    @Override
    public boolean test(ReadBlogOutput t) {
        if (!t.published() && published) {
            return false;
        }
        if (test == null) {
            return true;
        }
        return test.test(t);
    }
}
