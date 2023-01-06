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
package com.telenav.smithy.java.generators.size;

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
