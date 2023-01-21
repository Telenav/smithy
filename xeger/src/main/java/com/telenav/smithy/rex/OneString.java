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

import static com.telenav.smithy.rex.ElementKinds.STRING_LITERAL;
import static com.telenav.smithy.rex.RegexElement.escapeForDisplay;
import static com.telenav.smithy.rex.ShorthandCharacterClass.WORD_CHARS;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * An element which is a static string - e.g. in (?:yes|no), yes and no may each
 * be represented by one of these.
 */
final class OneString implements RegexElement, Confoundable<OneString> {

    final String string;
    private final boolean negated;

    OneString(String string, boolean negated) {
        if (string.charAt(0) == '\\') {
            throw new IllegalStateException("HEY! '" + string + "'");
        }
        this.string = string;
        this.negated = negated;
    }

    @Override
    public ElementKinds kind() {
        return STRING_LITERAL;
    }

    private String randomString(Random rnd) {
        String str;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < string.length(); i++) {
                char orig = string.charAt(i);
                char c = WORD_CHARS[rnd.nextInt(WORD_CHARS.length)];
                if (orig == c) {
                    c++;
                }
                sb.append(c);
            }
            str = sb.toString();
        } while (str.equals(string));
        return str;
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        if (negated) {
            into.append(string);
        } else {
            String rev = reversed();
            if (!rev.equals(string)) {
                into.append(rev);
            } else {
                into.append(randomString(rnd));
            }
        }
    }

    @Override
    public String toString() {
        return (negated ? "Negated(" : "")
                + escapeForDisplay(string)
                + (negated ? ")" : "");
    }

    private String reversed() {
        return new StringBuilder(string).reverse().toString();
    }

    @Override
    public boolean canConfound() {
        return !string.equals(reversed());
    }

    @Override
    public Optional<OneString> confound() {
        if (negated) {
            return Optional.of(new OneString(string, false));
        }
        String rev = reversed();
        if (rev.equals(string)) {
            return Optional.empty();
        }
        return Optional.of(new OneString(rev, negated));
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.string);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final OneString other = (OneString) obj;
        return Objects.equals(this.string, other.string);
    }

}
