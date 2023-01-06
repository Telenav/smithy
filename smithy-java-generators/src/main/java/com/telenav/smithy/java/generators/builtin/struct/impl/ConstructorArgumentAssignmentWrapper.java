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
