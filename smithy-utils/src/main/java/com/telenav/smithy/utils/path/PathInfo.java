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
package com.telenav.smithy.utils.path;

import java.util.Collections;
import java.util.Map;

/**
 * Contains either a valid URI path extracted from an HttpTrait, or a a valid
 * and correct regular expression representing the path.
 *
 * @author Tim Boudreau
 */
public final class PathInfo {

    final boolean isRegex;
    final String text;
    private final Map<Integer, String> inputMemberForRegexElement;

    PathInfo(boolean isRegex, String text, Map<Integer, String> inputMemberForRegexElement) {
        this.isRegex = isRegex;
        this.text = text;
        this.inputMemberForRegexElement = Collections.unmodifiableMap(inputMemberForRegexElement);
    }

    public boolean isRegex() {
        return isRegex;
    }

    public String text() {
        return text;
    }

    public Map<Integer, String> inputMemberForRegexElement() {
        return inputMemberForRegexElement;
    }

}
