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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Encapsulates all of the generators associated with declaring and assigning a
 * single member in a constructor.
 *
 * @author Tim Boudreau
 */
final class ConstructorArgumentWrapper<S extends Shape> implements Comparable<ConstructorArgumentWrapper<?>> {

    private final StructureMember<S> member;
    private final ConstructorArgumentGenerationWrapper<? extends S> generation;
    private final ConstructorArgumentAssignmentWrapper<? extends S> assignment;

    ConstructorArgumentWrapper(StructureMember<S> member, ConstructorArgumentGenerationWrapper<? extends S> generation, ConstructorArgumentAssignmentWrapper<? extends S> assignment) {
        this.member = member;
        this.generation = generation;
        this.assignment = assignment;
    }

    <T> void generateArguments(ClassBuilder.ConstructorBuilder<T> c, ClassBuilder<?> cb, ConstructorKind kind) {
        generation.generateArguments(c, cb, kind);
    }

    <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void generateChecks(B bb, ClassBuilder<?> cb, ConstructorKind kind, StructureGenerationHelper helper) {
        assignment.applyChecks(helper, bb, cb, kind);
    }

    <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void generateAssignments(B bb, ClassBuilder<?> cb, ConstructorKind kind, StructureGenerationHelper helper) {
        assignment.applyAssignment(helper, bb, cb, kind);
    }

    @Override
    public int compareTo(ConstructorArgumentWrapper<?> o) {
        return Double.compare(member.weight(), o.member.weight());
    }

}
