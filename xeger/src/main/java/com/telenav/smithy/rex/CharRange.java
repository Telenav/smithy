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
import static com.telenav.smithy.rex.RegexElement.escapeForDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Represents a character range such as `0-9`.
 */
final class CharRange implements ContainerRegexElement, Confoundable<CharRange> {

    int min = -1;
    int max = -1;
    OneChar start;
    OneChar end;
    private final boolean negated;

    CharRange(boolean negated) {
        this.negated = negated;
    }

    public CharRange(OneChar start, OneChar end, boolean negated) {
        this.start = start;
        this.end = end;
        this.negated = negated;
    }

    @Override
    public ContainerRegexElement prune() {
        return this;
    }

    @Override
    public boolean isEmpty() {
        return start == null && end == null;
    }

    @Override
    public ContainerRegexElement duplicate() {
        return this;
    }

    public void traverse(int depth, BiConsumer<Integer, RegexElement> c) {
        c.accept(depth + 1, this);
    }

    private List<Character> characters() {
        List<Character> chars = new ArrayList<>();
        int start = this.start == null ? 32 : this.start.cc;
        int end = this.end == null ? 128 : this.end.cc;
        if (negated) {
            for (int i = 0; i < start; i++) {
                chars.add((char) i);
            }
            for (int i = end; i < 128; i++) {
                chars.add((char) i);
            }
        } else {
            for (int i = start; i < end; i++) {
                chars.add((char) i);
            }
        }
        return chars;
    }

    private void subemit(StringBuilder into, Random rnd) {
        if (negated) {
            List<Character> chs = characters();
            into.append(chs.get(rnd.nextInt(chs.size())));
            return;
        }
        if (this.start == null && this.end == null) {
            into.append(">>>uh-oh<<<");
            return;
        }
        char limitChar = end == null ? (char) 127 : end.cc;
        char startChar = start.cc;
        int range = limitChar - startChar;
        int dest;
        if (range > 0) {
            dest = rnd.nextInt(range);
        } else {
            dest = 0;
        }
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
        return (negated ? "Negated(" : "")
                + (start == null
                        ? "??"
                        : escapeForDisplay(start.cc)) + "-"
                + (end == null
                        ? "??"
                        : escapeForDisplay(end.cc))
                + (negated ? ")" : "");
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

    @Override
    public boolean canConfound() {
        return min > 0 || max < 127;
    }

    @Override
    public Optional<CharRange> confound() {
        if (!canConfound()) {
            return Optional.empty();
        }
        if (start != null && start.cc > 33) {
            return Optional.of(new CharRange(new OneChar((char) 32, false), new OneChar((char) (start.cc - 1), false), negated));
        }
        return Optional.of(new CharRange(start, end, !negated));
    }

}
