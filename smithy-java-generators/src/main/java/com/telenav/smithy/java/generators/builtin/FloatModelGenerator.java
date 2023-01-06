/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;

import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
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
