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
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAssignmentGenerator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Holds a member, the assignment generator to use with it, and any validity
 * check generators that need to validate the argument before the assignment
 * code is generated.
 *
 * @author Tim Boudreau
 */
final class ConstructorArgumentAssignmentWrapper<S extends Shape> {

    private final StructureMember<S> member;
    private final ConstructorAssignmentGenerator<? super S> assignment;
    private final List<? extends ConstructorArgumentCheckGenerator<? super S>> checks;

    ConstructorArgumentAssignmentWrapper(StructureMember<S> member, ConstructorAssignmentGenerator<? super S> assignment, List<? extends ConstructorArgumentCheckGenerator<? super S>> checks) {
        this.member = member;
        this.assignment = assignment;
        this.checks = checks;
    }

    <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void applyChecks(StructureGenerationHelper helper, B bldr, ClassBuilder<?> cb, ConstructorKind kind) {
        for (ConstructorArgumentCheckGenerator<? super S> check : checks) {
            check.generateConstructorArgumentChecks(member, helper, cb, bldr, kind);
        }
    }

    <B extends ClassBuilder.BlockBuilderBase<T, B, ?>, T> void applyAssignment(StructureGenerationHelper helper, B bldr, ClassBuilder<?> cb, ConstructorKind kind) {
        assignment.generateConstructorAssignment(member, helper, bldr, cb, kind);
    }

}
