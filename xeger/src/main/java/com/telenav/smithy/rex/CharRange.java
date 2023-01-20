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

import static com.telenav.smithy.rex.ElementKinds.CHAR_RANGE;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Represents a character range such as `0-9`.
 */
final class CharRange implements ContainerRegexElement {

    int min = -1;
    int max = -1;
    OneChar start;
    OneChar end;

    CharRange() {
    }

    @Override
    public ContainerRegexElement duplicate() {
        return this;
    }
    
    public void traverse(Consumer<RegexElement> c) {
        c.accept(this);
        if (start != null) {
            start.traverse(c);
        }
        if (end != null) {
            end.traverse(c);
        }
    }

    private void subemit(StringBuilder into, Random rnd) {
        if (this.start == null && this.end == null) {
            into.append("!");
            return;
        }
        char limitChar = end == null ? (char) 127 : end.cc;
        char startChar = start.cc;
        int range = limitChar - startChar;
        int dest = rnd.nextInt(range);
        char c = (char) (startChar + dest);
        into.append(c);
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        if (min > 0 && max > 0) {
            int ct = max - min;
            if (ct > 0) {
                int total = rnd.nextInt(ct);
                for (int i = 0; i <= min + total; i++) {
                    subemit(into, rnd);
                }
            }
        } else if (min > 0) {
            int total = rnd.nextInt(8);
            for (int i = 0; i <= min + total; i++) {
                subemit(into, rnd);
            }
        } else {
            subemit(into, rnd);
        }
    }

    @Override
    public String toString() {
        return (start == null ? "??" : start.cc) + "-" + (end == null ? "??" : end.cc);
    }

    @Override
    public ElementKinds kind() {
        return CHAR_RANGE;
    }

    @Override
    public <T> T enter(Function<Consumer<RegexElement>, T> f) {
        return f.apply(this::add);
    }

    @Override
    public void add(RegexElement el) {
        if (!(el instanceof OneChar)) {
            throw new IllegalArgumentException("Not a OneChar: " + el.getClass().getSimpleName() + " " + el);
        }
        if (start == null) {
            start = (OneChar) el;
        } else if (end == null) {
            end = (OneChar) el;
        } else {
            throw new IllegalStateException("Three elements in character class: " + start + " " + end + " ??? " + el);
        }
    }

    @Override
    public void boundLast(int min, int max) {
        this.min = min;
        this.max = max;
    }

}
