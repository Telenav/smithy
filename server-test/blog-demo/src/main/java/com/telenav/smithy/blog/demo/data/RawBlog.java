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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mastfrog.util.strings.Strings;
import com.telenav.blog.model.Synopsis;
import com.telenav.blog.model.Tags;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

/**
 * The original circa-2011 nodejs blog engine format. There are a few quirks as
 * some files contain dates as numbers, other dates as iso format, and some tags
 * are a single comma delimited string, while others are a list.
 *
 * @author Tim Boudreau
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawBlog {

    public long lastModified;
    public Object modifiedDate;
    public Instant date;
    public Object keywords;
    public String synop;
    public String title;
    public String body;

    @SuppressWarnings("unchecked")
    public Tags tags() {
        if (keywords instanceof String) {
            String s = (String) keywords;
            if (s.indexOf(',') > 0) {
                return new Tags(s.split("\\s*,\\s*"));
            }
            return new Tags((String) keywords);
        } else if (keywords instanceof List<?>) {
            return new Tags(new TreeSet<>((List<String>) keywords));
        }
        return new Tags();
    }

    public Synopsis synopsis() {
        if (synop == null) {
            return new Synopsis(Strings.elide(body, 180).toString().replace('\n', ' ').trim());
        } else {
            return new Synopsis(synop);
        }
    }

    boolean isViable() {
        return title != null && body != null;
    }

    public Instant lastModified() {
        if (lastModified == 0) {
            if (modifiedDate != null) {
                if (modifiedDate instanceof String) {
                    Instant inst = Instant.parse(modifiedDate.toString());
                    return inst;
                }
                if (modifiedDate instanceof Long) {
                    lastModified = (Long) modifiedDate;
                }
                if (modifiedDate instanceof Instant) {
                    return ((Instant) modifiedDate);
                }
            }
        }
//        return Instant.ofEpochMilli(Math.max(lastModified, modifiedDate));
        return Instant.ofEpochMilli(lastModified);
    }

    public long hash() {
        long result = body.hashCode();
        result *= title.hashCode();
        return result;
    }

}
