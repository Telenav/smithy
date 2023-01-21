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

import static com.telenav.smithy.rex.ElementKinds.CHAR_CLASS;
import static com.telenav.smithy.rex.EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ONE;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * General purpose container which uses a selection strategy to decide how
 * elements are emitted.
 */
class GeneralBag extends AbstractContainerRegexElement<GeneralBag> {

    final EmittingElementSelectionStrategy choices;
    boolean negated;

    GeneralBag(ElementKinds kind, EmittingElementSelectionStrategy choices) {
        super(kind);
        this.choices = choices;
    }

    GeneralBag(ElementKinds kind, EmittingElementSelectionStrategy choices, List<RegexElement> contents) {
        super(kind, contents);
        this.choices = choices;
    }

    @Override
    public ElementKinds kind() {
        if (contents.isEmpty()) {
            return ElementKinds.EMPTY;
        }
        return super.kind();
    }

    @Override
    public GeneralBag duplicate() {
        GeneralBag nue = new GeneralBag(kind, choices);
        nue.negated = negated;
        nue.contents.addAll(duplicateElements());
        return nue;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (negated) {
            sb.append('^');
        }
        sb.append(super.toString());
        return sb.toString();
    }

    public GeneralBag negated() {
        this.negated = !negated;
        return this;
    }

    public GeneralBag negated(boolean val) {
        this.negated = val;
        return this;
    }

    @Override
    public Optional<GeneralBag> confound() {
        if (choices == ONE && CHAR_CLASS == kind) {
            GeneralBag nue = new GeneralBag(CHAR_CLASS, ONE, this.duplicateElements())
                    .negated(!negated);
            return Optional.of(nue);
        }
        return super.confound();
    }

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        if (negated && choices == EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ONE
                && ElementKinds.CHAR_CLASS == kind) {
            List<Character> chars = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                boolean matched = false;
                for (RegexElement re : contents) {
                    if (re instanceof OneChar oc) {
                        matched |= oc.cc == i;
                    } else if (re instanceof OneString os) {
                        matched |= os.string.indexOf((char) i) >= 0;
                    } else if (re instanceof ShorthandCharacterClass scc) {
                        matched |= scc.matches((char) i);
                    }
                    if (matched) {
                        break;
                    }
                }
                if (!matched) {
                    chars.add((char) i);
                }
            }
            // Pending:  handle subtraction and similar, like : [a-z&&[^m-p]]
            into.append(chars.get(rnd.nextInt(chars.size())));
            return;
        }
        choices.eachElement(contents, rnd, el -> {
            el.emit(into, rnd, backreferenceResolver);
        });
    }

    @Override
    GeneralBag newCopy(List<RegexElement> elements) {
        return new GeneralBag(kind, choices, elements);
    }

}
