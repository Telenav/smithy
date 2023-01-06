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
