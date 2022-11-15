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
import java.util.Collection;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates a field in a structure class for one member, applying any
 * decorators and documentation contributors.
 *
 * @author Tim Boudreau
 */
public interface FieldGenerator<S extends Shape> {

    /**
     * Add a field to the passed class builder, representing the passed member.
     *
     * @param <R> The class builder's parameter type
     * @param member The class member
     * @param annotators Any annotators that want to annotate the field
     * @param docContributors Any contributors that want to contribute to the
     * javadoc for the field
     * @param cb A class builder to add to
     */
    <R> void generateField(
            StructureMember<? extends S> member,
            Collection<? extends FieldDecorator<? super S>> annotators,
            Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors,
            ClassBuilder<R> cb);

}
