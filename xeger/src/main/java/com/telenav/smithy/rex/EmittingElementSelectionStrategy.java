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
import java.util.function.Consumer;

/**
 * Strategy for which children of a regular expression should be emitted.
 */
interface EmittingElementSelectionStrategy {

    void eachElement(List<RegexElement> elements, Random rnd, Consumer<RegexElement> c);

    public static enum EmittingElementSelectionStrategies implements EmittingElementSelectionStrategy {
        ALL, ONE;

        @Override
        public void eachElement(List<RegexElement> elements, Random rnd, Consumer<RegexElement> c) {
            if (elements.isEmpty()) {
                return;
            }
            switch (this) {
                case ALL:
                    elements.forEach(c);
                    break;
                case ONE:
                    c.accept(elements.get(rnd.nextInt(elements.size())));
                    break;
            }
        }
    }

}
