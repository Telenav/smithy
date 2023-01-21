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

import static com.telenav.smithy.rex.ElementKinds.CHAR_LITERAL;
import static com.telenav.smithy.rex.RegexElement.escapeForDisplay;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * A regex element that is a single character.
 */
final class OneChar implements RegexElement, Confoundable<OneChar> {

    final char cc;
    private final boolean negated;

    OneChar(char cc, boolean negated) {
        this.cc = cc;
        this.negated = negated;
    }

    @Override
    public ElementKinds kind() {
        return CHAR_LITERAL;
    }

    @Override
    public void emit(StringBuilder into, Random rnd,
            IntFunction<CaptureGroup> backreferenceResolver) {
        if (negated) {
            int base = 32;
            int range = 127 - base;
            int target = rnd.nextInt(range) + 32;
            if (target == cc) {
                target -= 1;
            }
            if (target < 32) {
                target = cc + 1;
            }
            into.append((char) target);
        } else {
            into.append(cc);
        }
    }

    @Override
    public String toString() {
        return escapeForDisplay(cc);
    }

    @Override
    public boolean canConfound() {
        return true;
    }

    @Override
    public Optional<OneChar> confound() {
        if (negated) {
            return Optional.of(new OneChar(cc, !negated));
        }
        int c2 = cc + 1;
        if (c2 > 126) {
            c2 = '.';
        }
        return Optional.of(new OneChar((char) c2, negated));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OneChar && ((OneChar) o).cc == cc;
    }

    @Override
    public int hashCode() {
        return cc * 631;
    }

}
