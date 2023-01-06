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

package com.telenav.smithy.java.generators.builtin.struct.impl;

import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
final class GenericConstructorDocumentationContributor implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final ConstructorKind kind;

    GenericConstructorDocumentationContributor(ConstructorKind kind) {
        this.kind = kind;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        switch (kind) {
            case JSON_DESERIALIZATON:
                docHead.append("Creates a new ").append(target.structure().getId().getName()).append('.');
                break;
            case SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES:
                docHead.append("Convenience constructor for ").append(target.structure().getId().getName()).append(" which substitutes primitive ints or doubles for bytes, shorts or floats, for ").append(target.structure().getId().getName()).append('.');
                break;
            case SECONDARY_WITH_PRIMITIVES:
                docHead.append("Convenience constructor for ").append(target.structure().getId().getName()).append(" which takes primitives, for").append(target.structure().getId().getName()).append('.');
        }
    }

}
