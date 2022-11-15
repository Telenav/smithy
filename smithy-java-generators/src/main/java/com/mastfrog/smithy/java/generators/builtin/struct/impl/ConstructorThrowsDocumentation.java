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
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 * Appends a &#064;throws clause to javadoc for a constructor, if there are
 * things validation or null-checks could fail for.
 *
 * @author Tim Boudreau
 */
final class ConstructorThrowsDocumentation implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final ConstructorKind kind;

    ConstructorThrowsDocumentation(ConstructorKind kind) {
        this.kind = kind;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        boolean hasRequiredNullableParameters = false;
        boolean hasConstraints = false;
        for (StructureMember<?> mem : target.members()) {
            if (mem.isRequired() && (!mem.isPrimitive() || !mem.hasDefault())) {
                hasRequiredNullableParameters = true;
            }
            MemberShape member = mem.member();
            hasConstraints |= member.getTrait(RangeTrait.class).isPresent() || member.getTrait(PatternTrait.class).isPresent() || member.getTrait(LengthTrait.class).isPresent();
            if (hasRequiredNullableParameters && hasConstraints) {
                break;
            }
        }
        if (hasRequiredNullableParameters || hasConstraints) {
            docTail.append("\n@throws ").append(target.validation().name()).append(' ');
            if (hasRequiredNullableParameters) {
                docTail.append("if required parameters are null");
                if (hasConstraints) {
                    docTail.append(" or ");
                }
            }
            if (hasConstraints) {
                docTail.append("if any parameters do not match their range, length or pattern " + "constraints in the schema of ").append(target.structure().getId());
            }
        }
    }

}
