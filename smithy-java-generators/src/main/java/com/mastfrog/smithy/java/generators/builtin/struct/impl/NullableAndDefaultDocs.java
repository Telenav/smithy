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
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Generates top-level documentation about any defaulted properties of a structure.
 */
final class NullableAndDefaultDocs implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        List<StructureMember<?>> mems = target.membersSortedByName();
        for (Iterator<StructureMember<?>> it = mems.iterator(); it.hasNext();) {
            StructureMember<?> sm = it.next();
            if (!sm.hasDefault() && sm.isRequired()) {
                it.remove();
            }
        }
        if (mems.isEmpty()) {
            return;
        }
        if (docHead.length() > 0 && docHead.charAt(docHead.length() - 1) != '\n') {
            docHead.append('\n');
        }
        docHead.append("<h2>Optional and Defaulted Properties</h2><ul>\n");
        for (StructureMember<?> mem : mems) {
            docHead.append("<li><b>").append(mem.jsonName()).append(" (<i>").append(mem.typeName()).append(")</i>").append("</b> &mdash; ");
            if (mem.hasDefault()) {
                docHead.append("Default value if null: ").append(mem.getDefault().get());
            } else if (!mem.isRequired()) {
                docHead.append("Optional property (may be null)");
            }
        }
        docHead.append("</ul>");
    }

}
