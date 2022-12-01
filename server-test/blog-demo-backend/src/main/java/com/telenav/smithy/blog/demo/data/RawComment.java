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

import com.mastfrog.util.strings.Escaper;
import com.telenav.blog.model.Email;
import java.nio.file.Path;
import java.time.Instant;

/**
 *
 * @author Tim Boudreau
 */
public final class RawComment {

    private static final long EPOCH = Instant.parse("2011-03-19T19:03:47.427Z").toEpochMilli() / 10000;

    public String author;
    public Email email;
    public String title;
    public String comment;
    public Instant date;
    public String originIP;

    boolean isViable() {
        return author != null && (comment != null || title != null);
    }

    String getId(Path path) {
        long delta = (date.toEpochMilli() / 10000) - EPOCH;
        String s = Long.toString(delta, 36) + path.getFileName().toString();
        int ix = s.lastIndexOf('.');
        s = s.substring(0, ix);
        s = Escaper.OMIT_NON_WORD_CHARACTERS.escape(s);
        if (s.length() > 16) {
            s = s.substring(0, 15);
        }
        while (s.length() < 12) {
            s += "x";
        }
        return s;

    }

    static String id(Path path) {
        String s = path.getFileName().toString();
        int ix = s.lastIndexOf('.');
        s = s.substring(0, ix);
        s = Escaper.OMIT_NON_WORD_CHARACTERS.escape(s);
        if (s.length() > 16) {
            s = s.substring(0, 15);
        }
        while (s.length() < 12) {
            s += "x";
        }
        return s;
    }
}
