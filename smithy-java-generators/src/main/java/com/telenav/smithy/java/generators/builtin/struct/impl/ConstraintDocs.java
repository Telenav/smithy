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

import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.Iterator;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 * Generates top-level documentation about any constrained properties of a structure.
 *
 * @author Tim Boudreau
 */
final class ConstraintDocs implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        List<StructureMember<?>> mems = target.membersSortedByName();
        for (Iterator<StructureMember<?>> it = mems.iterator(); it.hasNext();) {
            StructureMember<?> sm = it.next();
            if (!hasConstraints(sm)) {
                it.remove();
            }
        }
        if (mems.isEmpty()) {
            return;
        }
        if (docHead.length() > 0 && docHead.charAt(docHead.length() - 1) != '\n') {
            docHead.append('\n');
        }
        docHead.append("<h2>Constrained Properties</h2>\n<ul>\n");
        for (StructureMember<?> mem : mems) {
            docHead.append("<li><b>").append(mem.jsonName()).append(" (<i>").append(mem.typeName()).append(")</i>").append("</b> &mdash; ");
            mem.member().getTrait(RangeTrait.class).ifPresent(rng -> {
                rng.getMin().ifPresent(min -> {
                    docHead.append("Minimum: <code>").append(min).append("</code> ");
                });
                rng.getMax().ifPresent(max -> {
                    docHead.append("Maximum: <code>").append(max).append("</code> (inclusive)");
                });
            });
            mem.member().getTrait(LengthTrait.class).ifPresent(rng -> {
                rng.getMin().ifPresent(min -> {
                    docHead.append("Minimum Length: <code>").append(min).append("</code> ");
                });
                rng.getMax().ifPresent(max -> {
                    docHead.append("Maximum Length: <code>").append(max).append("</code> (inclusive)");
                });
            });
            mem.member().getTrait(PatternTrait.class).ifPresent(pat -> {
                docHead.append(" Must match pattern: <code>").append(pat.getValue()).append("</code>");
            });
            docHead.append("</li>\n");
        }
        docHead.append("</ul>\n");
    }


    private <S extends Shape> boolean hasConstraints(StructureMember<S> sm) {
        return sm.member().getTrait(RangeTrait.class).isPresent() || sm.member().getTrait(PatternTrait.class).isPresent() || sm.member().getTrait(LengthTrait.class).isPresent();
    }

}
