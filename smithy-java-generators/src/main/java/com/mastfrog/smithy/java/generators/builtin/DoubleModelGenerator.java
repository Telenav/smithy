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
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import static java.lang.Double.doubleToLongBits;
import java.nio.file.Path;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.DoubleShape;

/**
 *
 * @author Tim Boudreau
 */
final class DoubleModelGenerator extends AbstractNumberGenerator<DoubleShape> {

    private static final String LONG_VALUE_VAR = "_longValue";

    DoubleModelGenerator( DoubleShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language ) {
        super( shape, model, destSourceRoot, target, language );
    }

    @Override
    protected <T, R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void generateEqualsComparison( String other,
            ClassBuilder<?> cb, B bb ) {
        doubleToLongBits( 3 );
        bb.returning( "Double.doubleToLongBits(" + VALUE_FIELD + ") == "
                + "Double.doubleToLongBits(" + other + "." + VALUE_FIELD + ")" );
    }

    @Override
    protected <T, R> void generateHashCodeComputation( ClassBuilder<T> cb,
            BlockBuilder<R> bb ) {

        bb.declare( LONG_VALUE_VAR )
                .initializedByInvoking( "doubleToLongBits" )
                .withArgument( VALUE_FIELD )
                .on( "Double" )
                .as( "long" );
        int p = (int) prime( cb.fqn() );
        bb.returning( p + " * (int) (" + LONG_VALUE_VAR + " ^ (" + LONG_VALUE_VAR + " >>> 32))" );
    }
}
