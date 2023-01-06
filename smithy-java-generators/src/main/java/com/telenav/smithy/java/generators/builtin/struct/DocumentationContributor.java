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
package com.telenav.smithy.java.generators.builtin.struct;

import java.util.Collection;
import java.util.Optional;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Contributes documentation of something.
 *
 * @author Tim Boudreau
 */
public interface DocumentationContributor<S extends Shape, M> {

    /**
     * Generate documentation - the documentation is split into two parts, so
     * that, e.g. things that handle parameters can append &#064;param elements
     * to the tail portion while also contributing to the top. They will be
     * combined, if non-empty, to form the final documentation.
     *
     * @param target The thing being documented
     * @param docHead The top portion of the documentation
     * @param docTail The tail of the documentation
     */
    void generateDocumentation(
            M target,
            StringBuilder docHead,
            StringBuilder docTail);

    /**
     * Combine some number of documentation contributors, in order, in order to
     * generate final documentation.
     *
     * @param <S> The member type
     * @param <M> A shape type
     * @param member The member
     * @param docContributors The contributors
     * @return A string if the resulting documentation is non-empty
     */
    static <S extends Shape, M> Optional<String> document(
            M member,
            Collection<? extends DocumentationContributor<? super S, ? super M>> docContributors) {
        if (docContributors.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder head = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        docContributors.forEach(d -> {
            d.generateDocumentation(member, head, tail);
        });
        if (tail.length() > 0) {
            if (head.length() > 0 && tail.charAt(0) != '\n') {
                head.append('\n');
            }
            head.append(tail);
        }
        if (head.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(head.toString());
    }
}
