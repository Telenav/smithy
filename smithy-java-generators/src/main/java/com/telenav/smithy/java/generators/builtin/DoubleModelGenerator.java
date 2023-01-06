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
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
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
