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
import com.mastfrog.java.vogon.ParameterConsumer;
import java.util.List;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Adds a constructor argument to a constructor, applying the passed set of
 * annotators.
 *
 * @author Tim Boudreau
 */
public interface ConstructorArgumentGenerator<S extends Shape> {

    /**
     * Generate a single constructor argument for the passed structure member,
     * given the constructor kind. Note that the passed builder may be a factory
     * <i>method</i> builder rather than a constructor builder - implementations
     * should work with either.
     *
     * @param <P> The type of builder
     * @param <R> The return type of the builder
     * @param member The structure member
     * @param annotators The set of annotators that have requested the ability
     * to annotate this constructor argument
     * @param cb The class builder (for adding imports, or creating fields with
     * default values, or similar)
     * @param con The builder
     * @param ck The kind of constructor being built
     */
    <P extends ParameterConsumer<R>, R> void generateConstructorArgument(
            StructureMember<? extends S> member,
            List<? extends ConstructorArgumentAnnotator<? super S>> annotators,
            ClassBuilder<?> cb,
            P con,
            ConstructorKind ck);

}
