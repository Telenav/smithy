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
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.telenav.validation.ValidationExceptionProvider;
import static com.telenav.validation.ValidationExceptionProvider.validationExceptions;
import static java.lang.Double.doubleToLongBits;
import static java.lang.Float.floatToIntBits;
import java.nio.file.Path;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.FloatShape;

/**
 *
 * @author Tim Boudreau
 */
final class FloatModelGenerator extends AbstractNumberGenerator<FloatShape> {

    FloatModelGenerator( FloatShape shape, Model model,
            Path destSourceRoot, GenerationTarget target, LanguageWithVersion language ) {
        super( shape, model, destSourceRoot, target, language );
    }

    @Override
    protected <T, R> void annotateConstructor( ClassBuilder<T> cb, ClassBuilder.ConstructorBuilder<R> con ) {
        cb.generateDebugLogCode();
        super.annotateConstructor( cb, con );
    }

    @Override
    protected <T, R> void generateHashCodeComputation( ClassBuilder<T> cb,
            BlockBuilder<R> bb ) {
        int p = (int) prime( cb.fqn() );
        floatToIntBits( 0 );
        bb.returning( p + " * Float.floatToIntBits(" + VALUE_FIELD + ")" );
    }

    @Override
    protected <T, R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void generateEqualsComparison( String other,
            ClassBuilder<?> cb, B bb ) {
        doubleToLongBits( 3 );
        bb.returning( "Float.floatToIntBits(" + VALUE_FIELD + ") == "
                + "Float.floatToIntBits(" + other + "." + VALUE_FIELD + ")" );
    }

    @Override
    protected void generateAlternateConstructors( ClassBuilder<String> cb ) {
        cb.method("__checkDoubleValue", mth -> {
            mth.withModifier( PRIVATE, STATIC )
                    .addArgument( "double", "doubleValue" )
                    .returning( "float" )
                    .body(bb -> {
                        IfBuilder<?> test = bb.iff().booleanExpression(
                                "doubleValue < -Float.MAX_VALUE || doubleValue > Float.MAX_VALUE" );
                        validationExceptions().createThrow( cb, test,
                                "Value must be within the bounds of Float but is", "doubleValue" );
                        test.endIf();
                        bb.returningValue().castTo( "float" ).expression( "doubleValue" );
                    } );
        } );

        cb.constructor(con -> {
            con.docComment("Convenience constructor which takes a <code>double</code>."
                    + "\n@param doubleValue A double, which must be within the bounds that that class requires and those of <code>float</code>."
                    + "\n@throws " + validationExceptions().name() + " if the value is out of the range Float.MIN_VALUE to Float.MAX_VALUE" )
                    .addArgument( "double", "doubleValue" )
                    .setModifier( PUBLIC )
                    .body( bb -> {
                        bb.invoke( "this" )
                                .withArgumentFromInvoking( "__checkDoubleValue" )
                                .withArgument( "doubleValue" )
                                .inScope()
                                .inScope();
                    } );
        } );
    }

}
