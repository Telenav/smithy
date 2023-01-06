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
package com.telenav.smithy.java.generators.builtin.struct;

import com.mastfrog.java.vogon.Annotatable;
import com.mastfrog.java.vogon.Imports;

/**
 * Applies one or more annotation or other modification to a constructor under
 * generation.
 *
 * @author Tim Boudreau
 */
public interface ConstructorAnnotator {

    /**
     * Add any annotations to the constructor being generated here (note the
     * passed builder *may* be a MethodBuilder for a factory method, not a
     * constructor - implementations should be usable for either).
     *
     * @param <T> The return type of the annotatable builder
     * @param <A> The type of the annotatable builder
     * @param bldr The builder
     * @param on The class builder, in case imports need to be added
     * @param kind The kind of constructor being built
     * @param helper The structure it is being built for
     */
    <T, A extends Annotatable<T, A>> void apply(A bldr, Imports<?, ?> on,
            ConstructorKind kind, StructureGenerationHelper helper);
}
