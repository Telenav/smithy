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

import java.util.List;
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

    @Override
    public void emit(StringBuilder into, Random rnd, IntFunction<CaptureGroup> backreferenceResolver) {
        choices.eachElement(contents, rnd, el -> {
            el.emit(into, rnd, backreferenceResolver);
        });
    }

    @Override
    GeneralBag newCopy(List<RegexElement> elements) {
        return new GeneralBag(kind, choices, elements);
    }

}
