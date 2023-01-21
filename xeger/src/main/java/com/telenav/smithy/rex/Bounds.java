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

import static com.telenav.smithy.rex.Xeger.MAX_LENGTH;
import static java.lang.Character.MAX_VALUE;
import static java.lang.Math.min;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Wraps a container element and calls it the number of times to generate a
 * string matching the required bounds.
 */
final class Bounds implements ContainerRegexElement, Confoundable<Bounds> {

    ContainerRegexElement delegate;
    final int min;
    final int max;

    Bounds(ContainerRegexElement delegate, int min) {
        this(delegate, min, min);
    }

    Bounds(ContainerRegexElement delegate, int min, int max) {
        this.delegate = delegate;
        this.min = min;
        this.max = max;
    }

    @Override
    public boolean isEmpty() {
        return delegate == null || delegate.isEmpty();
    }

    @Override
    public ContainerRegexElement prune() {
        delegate.prune();
        return this;
    }

    @Override
    public Bounds duplicate() {
        Bounds nue = new Bounds((ContainerRegexElement) delegate.duplicate(), min, max);
        return nue;
    }

    public void traverse(int depth, BiConsumer<Integer, RegexElement> c) {
        c.accept(depth, this);
        delegate.traverse(depth + 1, c);
    }

    @Override
    public String toString() {
        StringBuilder base = new StringBuilder(delegate.toString());
        if (min == 0 && max == 1) {
            base.append('?');
        } else if (min == max) {
            base.append('{').append(min).append('}');
        } else if (max == MAX_VALUE - 1) {
            if (min == 0) {
                base.append('*');
            } else {
                base.append('+');
            }
        } else {
            base.append('{').append(min).append(',').append(max).append('}');
        }
        return base.toString();
    }

    @Override
    public <T> T enter(Function<Consumer<RegexElement>, T> f) {
        return delegate.enter(f);
    }

    @Override
    public void add(RegexElement el) {
        delegate.add(el);
    }

    @Override
    public ElementKinds kind() {
        return delegate.kind();
    }

    @Override
    public void emit(StringBuilder into, Random rnd,
            IntFunction<CaptureGroup> backreferenceResolver) {
        int count = max - min;
        if (count == Integer.MAX_VALUE) {
            // avoid overflow
            count /= 2;
        }
        int target = min;
        if (count > 1) {
            int inp = min(MAX_LENGTH, count + 1);
            if (inp > 0) {
                target += rnd.nextInt(inp);
            } else {
                throw new IllegalStateException("Bounds insane: min: " + min + " max " + max
                        + " max-min=" + count + " inp " + inp);
            }
        }
        for (int i = 0; i < target; i++) {
            delegate.emit(into, rnd, backreferenceResolver);
        }
    }

    @Override
    public void boundLast(int min, int max) {
        throw new IllegalStateException("Cannot bound a bounds");
    }

    @Override
    public boolean canConfound() {
        boolean result = min > 1 || max < 256;
        if (!result && delegate instanceof Confoundable<?> con) {
            result = con.canConfound();
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Bounds> confound() {
        ContainerRegexElement del
                = (ContainerRegexElement) delegate.as(Confoundable.class).flatMap(con -> {
                    return con.confound();
                }).orElse(delegate);
        if (del == delegate) {
            System.out.println("NOT CONFOUNDABLE: " + delegate.getClass().getSimpleName() + " " + del);
        }
        int newMin, newMax;
        if (min > 1 || (max < 256 && max > 1)) {
            newMin = max + 1;
            newMax = max + 5;
        } else {
            newMin = min;
            newMax = max;
        }
        if (del == delegate && newMin == min && newMax == max) {
            return Optional.empty();
        }
        return Optional.of(new Bounds(del, newMin, newMax));
    }

}
