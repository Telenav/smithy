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
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * An element which is a static string - e.g. in (?:yes|no), yes and no may each
 * be represented by one of these.
 */
final class OneString implements RegexElement, Confoundable<OneString> {

    final String string;

    OneString(String string) {
        this.string = string;
    }

    @Override
    public ElementKinds kind() {
        return STRING_LITERAL;
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        into.append(string);
    }

    @Override
    public String toString() {
        return "\"" + string + "\"";
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
        String rev = reversed();
        if (rev.equals(string)) {
            return Optional.empty();
        }
        return Optional.of(new OneString(rev));
    }

}
