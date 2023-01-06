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
