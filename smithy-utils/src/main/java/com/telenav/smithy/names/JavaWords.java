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
package com.telenav.smithy.names;

import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
public final class JavaWords /* implements ReservedWords */ {

    // This list MUST remain sorted
    private static final String[] JAVA_RESERVED = new String[]{
        "abstract", "assert", "boolean", "break", "byte", "case",
        "catch", "char", "class", "const", "continue", "default",
        "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected",
        "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw",
        "throws", "transient", "try", "void", "volatile", "while"
    };

    public static String escape(String word) {
        if (isReserved(word)) {
            return '_' + word;
        }
        return word;
    }

    public static boolean isReserved(String word) {
        return isJavaReservedWord(word);
    }

    public static boolean isJavaReservedWord(String word) {
        return Arrays.binarySearch(JAVA_RESERVED, word) >= 0;
    }

}
