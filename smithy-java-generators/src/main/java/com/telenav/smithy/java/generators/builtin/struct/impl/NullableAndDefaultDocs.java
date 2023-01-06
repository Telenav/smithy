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
