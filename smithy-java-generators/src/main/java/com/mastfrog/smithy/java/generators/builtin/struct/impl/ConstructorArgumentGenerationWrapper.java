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
package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorKind;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Maps a member to a generator for its corresponding constructor argument, and
 * a collection of annotators which may want to add annotations to the argument.
 *
 * @author Tim Boudreau
 */
final class ConstructorArgumentGenerationWrapper<S extends Shape> {

    private final StructureMember<S> member;
    private final ConstructorArgumentGenerator<S> generator;
    private final List<? extends ConstructorArgumentAnnotator<? super S>> annnotators;

    ConstructorArgumentGenerationWrapper(StructureMember<S> member, ConstructorArgumentGenerator<S> generator, List<? extends ConstructorArgumentAnnotator<? super S>> annnotators) {
        this.member = member;
        this.generator = generator;
        this.annnotators = annnotators;
    }

    <T> void generateArguments(ClassBuilder.ConstructorBuilder<T> con, ClassBuilder<?> cb, ConstructorKind kind) {
        generator.generateConstructorArgument(member, annnotators, cb, con, kind);
    }

}
