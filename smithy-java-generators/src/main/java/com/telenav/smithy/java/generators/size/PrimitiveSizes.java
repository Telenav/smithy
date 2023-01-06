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
package com.telenav.smithy.java.generators.size;

import com.telenav.smithy.names.NumberKind;

/**
 *
 * @author Tim Boudreau
 */
interface PrimitiveSizes {

    public static PrimitiveSizes COMPRESSED_OOPS = () -> 4;
    public static PrimitiveSizes UNCOMPRESSED_OOPS = () -> 8;

    default int sizeOf(NumberKind k, boolean isObject) {
        int sz = sizeOf(k);
        if (isObject) {
            sz += objectHeaderSize();
        }
        return sz;
    }

    default int sizeOf(NumberKind k) {
        switch (k) {
            case BYTE:
                return byteSize();
            case DOUBLE:
                return doubleSize();
            case FLOAT:
                return floatSize();
            case INT:
                return intSize();
            case LONG:
                return longSize();
            case SHORT:
                return shortSize();
            default:
                throw new AssertionError(k);
        }
    }

    default int objectHeaderSize() {
        return 12;
    }

    default int booleanSize() {
        return 1;
    }

    default int byteSize() {
        return 1;
    }

    default int shortSize() {
        return 2;
    }

    default int charSize() {
        return 2;
    }

    default int intSize() {
        return 4;
    }

    default int floatSize() {
        return 4;
    }

    default int longSize() {
        return 8;
    }

    default int doubleSize() {
        return 8;
    }

    int objectReferenceSize();

}
