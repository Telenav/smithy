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

import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
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
            return ElementKinds.REGEX;
        }

        @Override
        public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
            // do nothing
        }
    };

    default void traverse(Consumer<RegexElement> c) {
        c.accept(this);
    }

    default <T> Optional<T> as(Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }
}
