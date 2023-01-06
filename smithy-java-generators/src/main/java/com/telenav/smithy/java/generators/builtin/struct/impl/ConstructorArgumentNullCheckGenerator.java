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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorArgumentCheckGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a null check for an argument.
 *
 * @author Tim Boudreau
 */
final class ConstructorArgumentNullCheckGenerator<S extends Shape> implements ConstructorArgumentCheckGenerator<S> {

    @Override
    public <T, B extends ClassBuilder.BlockBuilderBase<T, B, ?>> void generateConstructorArgumentChecks(StructureMember<? extends S> member, StructureGenerationHelper structureOwner, ClassBuilder<?> addTo, B bb, ConstructorKind kind) {
        structureOwner.validation().createNullCheck(member.arg(), addTo, bb);
    }

}
