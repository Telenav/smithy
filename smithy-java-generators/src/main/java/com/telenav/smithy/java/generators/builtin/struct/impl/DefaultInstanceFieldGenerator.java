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

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAssignmentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultInstanceFieldGenerator implements StructureContributor {

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        ClassBuilder.FieldBuilder<ClassBuilder<T>> fld = cb.field("DEFAULT").withModifier(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL).docComment("Default instance of " + cb.className() + " using " + "the default values on this type's schema and any constituent" + " structure types' default fields.").initializedWithNew(nb -> {
            for (StructureMember<?> mem : helper.members()) {
                if (mem.target().getType() == ShapeType.STRUCTURE) {
                    nb.withArgument(mem.typeName() + ".DEFAULT");
                } else {
                    nb.withArgument(ConstructorAssignmentGenerator.defaultFieldName(mem));
                }
            }
            nb.ofType(cb.className());
        });
        fld.ofType(cb.className());
    }

}
