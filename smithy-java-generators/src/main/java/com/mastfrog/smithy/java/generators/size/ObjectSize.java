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

package com.mastfrog.smithy.java.generators.size;

/**
 *
 * @author Tim Boudreau
 */
public class ObjectSize implements Comparable<ObjectSize> {

    final long shallowSize;
    long minimumDeepSize;
    long maximumDeepSize;
    boolean deepSizeIsReliable;

    ObjectSize(long shallowSize) {
        this.shallowSize = shallowSize;
        minimumDeepSize = shallowSize;
        maximumDeepSize = shallowSize;
        deepSizeIsReliable = true;
    }
    
    public static ObjectSize combine(ObjectSize a, ObjectSize b) {
        ObjectSize result = new ObjectSize(a.shallowSize + b.shallowSize);
        result.minimumDeepSize = a.minimumDeepSize + b.minimumDeepSize;
        result.maximumDeepSize = a.maximumDeepSize + b.maximumDeepSize;
        return result;
    }
    
    public boolean isMaximumSizeGuessed() {
        return !deepSizeIsReliable;
    }

    public long shallowSize() {
        return shallowSize;
    }

    public long minimumDeepSize() {
        return minimumDeepSize;
    }

    public long maximumDeepSize() {
        return maximumDeepSize;
    }

    @Override
    public int compareTo(ObjectSize o) {
        if (deepSizeIsReliable && o.deepSizeIsReliable) {
            int result = Long.compare(maximumDeepSize, o.maximumDeepSize);
            if (result != 0) {
                return result;
            }
        }
        int result = Long.compare(minimumDeepSize, o.minimumDeepSize);
        if (result != 0) {
            return result;
        }
        return Long.compare(shallowSize, o.shallowSize);
    }

    void add(ObjectSize sz) {
        minimumDeepSize += sz.minimumDeepSize;
        maximumDeepSize += sz.maximumDeepSize;
        deepSizeIsReliable &= sz.deepSizeIsReliable;
    }

    void addMinMax(ObjectSize sz, long min, long max) {
        minimumDeepSize += sz.minimumDeepSize * min;
        maximumDeepSize += sz.maximumDeepSize * max;
    }

}
