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
import java.util.Random;
import java.util.function.IntFunction;

/**
 * The . character in a regular expression. We limit the actual range to ascii
 * characters 32 and above (space and up), since regular expressions are used in
 * generated code, and escaping them can make the generated code difficult to
 * read.
 *
 * @author Tim Boudreau
 */
final class AnyChar implements RegexElement {

    static final AnyChar ANY_CHAR = new AnyChar();

    @Override
    public ElementKinds kind() {
        return CHAR_LITERAL;
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        // Since these are used in generated code, stick with non control characters
        // that don't require escaping for readability.
        int range = 127 - 32;
        into.append((char) (32 + rnd.nextInt(range)));
    }

}
