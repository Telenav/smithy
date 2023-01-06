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
package com.telenav.smithy.java.generators.builtin.struct.impl;

import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
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
