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

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Regex element that acts as a container.
 *
 * @author Tim Boudreau
 */
interface ContainerRegexElement extends RegexElement {

    /**
     * During parsing, enter this container making it the one add calls delegate
     * to.
     *
     * @param <T>
     * @param f
     * @return
     */
    <T> T enter(Function<Consumer<RegexElement>, T> f);

    /**
     * Add an element to this container.
     *
     * @param el An element
     */
    void add(RegexElement el);

    /**
     * Apply the passed bounds to the last element added to this container.
     *
     * @param min The minimum
     * @param max The maximum
     */
    void boundLast(int min, int max);

    @Override
    ContainerRegexElement duplicate();

}
