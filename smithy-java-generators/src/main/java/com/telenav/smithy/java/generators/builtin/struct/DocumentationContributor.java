/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
