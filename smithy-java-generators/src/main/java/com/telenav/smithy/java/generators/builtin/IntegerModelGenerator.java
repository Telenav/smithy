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
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import java.nio.file.Path;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.IntegerShape;

/**
 *
 * @author Tim Boudreau
 */
final class IntegerModelGenerator extends AbstractNumberGenerator<IntegerShape> {

    IntegerModelGenerator(IntegerShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected <T, R> void generateHashCodeComputation(ClassBuilder<T> cb,
            BlockBuilder<R> bb) {
        int p = (int) prime(cb.fqn());
        bb.returning(variable(VALUE_FIELD).times(p).toString());
    }
}
