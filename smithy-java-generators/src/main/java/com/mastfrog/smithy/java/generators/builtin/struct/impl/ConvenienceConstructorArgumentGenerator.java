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
import com.mastfrog.java.vogon.ClassBuilder.MultiAnnotatedArgumentBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ParameterNameBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeNameBuilder;
import com.mastfrog.java.vogon.ParameterConsumer;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorArgumentAnnotator;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorArgumentGenerator;
import com.mastfrog.smithy.java.generators.builtin.struct.ConstructorKind;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a secondary constructor that substitutes ints for shorts/bytes and
 * doubles for floats (and performs range checks to ensure values will fit).
 *
 * @author Tim Boudreau
 */
final class ConvenienceConstructorArgumentGenerator<S extends Shape> implements ConstructorArgumentGenerator<S> {

    @Override
    public <P extends ParameterConsumer<R>, R> void generateConstructorArgument(StructureMember<? extends S> member, List<? extends ConstructorArgumentAnnotator<? super S>> annotators, ClassBuilder<?> cb, P con, ConstructorKind ck) {
        if (annotators.isEmpty()) {
            con.addArgument(member.convenienceConstructorTypeName(), member.arg());
        } else {
            MultiAnnotatedArgumentBuilder<ParameterNameBuilder<TypeNameBuilder<R>>> multi = con.addMultiAnnotatedArgument();
            annotators.forEach(anno -> {
                anno.generateConstructorArgumentAnnotations(member, ck, multi, cb);
            });
            multi.closeAnnotations().named(member.arg()).ofType(member.convenienceConstructorTypeName());
        }
    }

}
