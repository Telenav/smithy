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

import static com.telenav.smithy.rex.ElementKinds.CAPTURE_GROUP;
import static com.telenav.smithy.rex.EmittingElementSelectionStrategy.EmittingElementSelectionStrategies.ALL;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

/**
 * A capture group, whose last output can be replayed for back-references.
 *
 * @author Tim Boudreau
 */
final class CaptureGroup extends GeneralBag {

    private String lastEmitted;

    CaptureGroup() {
        super(CAPTURE_GROUP, ALL);
    }

    CaptureGroup(List<RegexElement> l) {
        super(CAPTURE_GROUP, ALL, l);
    }

    public String lastEmitted() {
        return lastEmitted;
    }

    @Override
    public void emit(StringBuilder into, Random rnd,
            IntFunction<CaptureGroup> backreferenceResolver) {
        StringBuilder nue = new StringBuilder();
        super.emit(nue, rnd, backreferenceResolver);
        into.append(nue);
        lastEmitted = nue.toString();
    }

    @Override
    GeneralBag newCopy(List<RegexElement> elements) {
        return new CaptureGroup(elements);
    }

    @Override
    public GeneralBag duplicate() {
        return new CaptureGroup(duplicateElements());
    }

}
