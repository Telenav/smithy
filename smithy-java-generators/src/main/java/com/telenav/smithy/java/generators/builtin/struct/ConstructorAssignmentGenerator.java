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
package com.telenav.smithy.java.generators.builtin.struct;

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
