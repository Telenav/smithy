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
import com.mastfrog.java.vogon.ClassBuilder.StringConcatenationBuilder;

/**
 * Adds contents not specific to a member to the toString() method of a
 * structure.
 */
public interface HeadTailToStringContributor {

    /**
     * Contribute code to the toString() method; if you need to do some
     * complicated computation to derive the string value, it is an option to
     * generate a new private method on the passed class builder, and append the
     * invocation of that method to the StringConcatenationBuilder.
     *
     * @param helper The structure
     * @param cb The class builder being generated into (for adding imports or
     * utility members to)
     * @param concat The concatentation that will be the return value of
     * toString().
     */
    void contributeToToString(StructureGenerationHelper helper, ClassBuilder<?> cb,
            StringConcatenationBuilder<?> concat);

}
