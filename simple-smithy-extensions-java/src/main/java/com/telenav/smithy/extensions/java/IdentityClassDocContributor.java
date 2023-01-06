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
