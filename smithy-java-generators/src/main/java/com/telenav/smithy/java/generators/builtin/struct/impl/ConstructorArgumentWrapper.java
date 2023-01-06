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
