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
package com.telenav.smithy.rex;

import static java.lang.Integer.min;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * A single component of a regular expression, which can emit some characters
 * that will match the pattern it expresses.
 *
 * @author Tim Boudreau
 */
interface RegexElement {

    /**
     * The kind of element.
     *
     * @return
     */
    ElementKinds kind();

    /**
     * Emit some characters which will match this element into the passed
     * StringBuilder.
     *
     * @param into The place to put the output
     * @param rnd A random to use for selecting what to do for elements that
     * need to choose branches or characters from the set available to it
     * @param backreferenceResolver Can resolve \$N backreferences into the last
     * emitted output
     */
    void emit(StringBuilder into, Random rnd,
            IntFunction<CaptureGroup> backreferenceResolver);

    default RegexElement duplicate() {
        return this;
    }

    static RegexElement EMPTY = new RegexElement() {
        @Override
        public ElementKinds kind() {
            return ElementKinds.EMPTY;
        }

        @Override
        public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
            // do nothing
        }
    };

    default void traverse(int depth, BiConsumer<Integer, RegexElement> c) {
        c.accept(depth, this);
    }

    default <T> Optional<T> as(Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    static String escapeForDisplay(String what) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < what.length(); i++) {
            char c = what.charAt(i);
            escapeForDisplay(c, sb);
        }
        return sb.toString();
    }

    static String escapeForDisplay(char c) {
        StringBuilder sb = new StringBuilder(4);
        escapeForDisplay(c, sb);
        return sb.toString();
    }

    static int countForMinMax(int min, int max, Random rnd) {
        int count;
        if (min == -1 && max == -1) {
            count = 1;
        } else if (min > 0 && (max == -1 || max > Integer.MAX_VALUE / 2)) {
            count = min + rnd.nextInt(12);
        } else if (min > 0 && max > 0) {
            if (min == max) {
                count = min;
            } else {
                int range = min(16, max - min);
                count = min + rnd.nextInt(range);
            }
        } else {
            count = 1;
        }
        return count;
    }

    static void escapeForDisplay(char c, StringBuilder sb) {
        switch (c) {
            case '\t':
                sb.append("\\t");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\b':
                sb.append("\\b");
                break;
            default:
                if (c >= 32 && c < 127) {
                    sb.append(c);
                } else if (c < 32) {
                    String s = Integer.toHexString(c).toLowerCase();

                    sb.append("\\x");
                    if (s.length() == 1) {
                        sb.append("0");
                    }
                    sb.append(s);
                } else if (c > 127) {
                    sb.append("\\u");
                    String val = Integer.toHexString(c).toLowerCase();
                    for (int j = 0; j < 4 - val.length(); j++) {
                        sb.append('0');
                    }
                    sb.append(val);
                }
        }
    }
}
