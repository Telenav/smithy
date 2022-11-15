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

import com.mastfrog.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
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
