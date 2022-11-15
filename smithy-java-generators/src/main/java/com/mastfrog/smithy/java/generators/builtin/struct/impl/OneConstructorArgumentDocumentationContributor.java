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

package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorKind;
import com.mastfrog.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
final class OneConstructorArgumentDocumentationContributor<S extends Shape> implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final StructureMember<S> member;
    private final ConstructorKind kind;

    OneConstructorArgumentDocumentationContributor(StructureMember<S> member, ConstructorKind kind) {
        this.member = member;
        this.kind = kind;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        docTail.append("\n@param ").append(member.arg()).append(' ');
        if (kind == ConstructorKind.JSON_DESERIALIZATON) {
            member.member().getTrait(DefaultTrait.class).ifPresentOrElse(def -> {
                docTail.append(" - <i>defaulted to ").append(def.toNode().toNode().toString().replaceAll("\n", "\\n")).append(" if null</i>");
            }, () -> {
                if (member.isRequired()) {
                    docTail.append(" - <i>required</i> - ");
                } else {
                    docTail.append(" - <i>may be null</i> - ");
                }
            });
        }
        member.member().getTrait(DocumentationTrait.class).or(() -> member.target().getTrait(DocumentationTrait.class)).ifPresentOrElse(dox -> docTail.append(dox.getValue()), () -> docTail.append("a ").append(member.typeName()));
    }

}
