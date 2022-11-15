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
package com.mastfrog.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.util.strings.Strings;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Assigns a one instance field from a constructor argument - this may involve
 * casts or conversions in some cases.
 *
 * @author Tim Boudreau
 */
public interface ConstructorAssignmentGenerator<S extends Shape> {

    <T, B extends BlockBuilderBase<T, B, ?>>
            void generateConstructorAssignment(
                    StructureMember<? extends S> member,
                    StructureGenerationHelper helper,
                    B bb,
                    ClassBuilder<?> cb,
                    ConstructorKind ck
            );

    /**
     * A general-purpose constructor assignment generator that applies defaults
     * if needed and assigns the field.
     */
    public static ConstructorAssignmentGenerator<Shape> DEFAULT
            = new DefaultConstructorAssignmentGenerator();

    static String defaultFieldName(StructureMember<?> member) {
        String defFieldName = "DEFAULT_"
                + Strings.camelCaseToDelimited(member.field(), '_').toUpperCase();
        return defFieldName;
    }

}
