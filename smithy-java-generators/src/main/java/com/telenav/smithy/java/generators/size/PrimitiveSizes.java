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
