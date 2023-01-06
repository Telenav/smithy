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
