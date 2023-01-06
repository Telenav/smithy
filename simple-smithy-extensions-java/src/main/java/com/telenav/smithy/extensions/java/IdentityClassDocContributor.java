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

package com.telenav.smithy.extensions.java;

import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.Iterator;
import java.util.Set;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
class IdentityClassDocContributor implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final Set<StructureMember<?>> idMembers;

    public IdentityClassDocContributor(Set<StructureMember<?>> idMembers) {
        this.idMembers = idMembers;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        docTail.append("<h2>Instance Identity and Equality</h2>\nIdentity for this class for the purposes of <code>equals()</code> and " + "<code>hashCode()</code> is supplied solely by the ");
        if (idMembers.size() > 1) {
            docTail.append("properties <b>");
        } else {
            docTail.append("property <b>");
        }
        for (Iterator<StructureMember<?>> it = idMembers.iterator(); it.hasNext();) {
            StructureMember<?> mem = it.next();
            docTail.append(mem.jsonName());
            if (it.hasNext()) {
                docTail.append(", ");
            }
        }
        docTail.append("</b>.\n");
    }

}
