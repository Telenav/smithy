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
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.names.JavaTypes;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;

/**
 *
 * @author Tim Boudreau
 */
final class ListModelGenerator extends AbstractListAndSetGenerator<ListShape> {

    ListModelGenerator( ListShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language ) {
        super( shape, model, destSourceRoot, target, language );
    }

    @Override
    protected String abstractType() {
        return "AbstractList";
    }

    @Override
    protected String type() {
        return "List";
    }

    @Override
    protected String internalType() {
        return "ArrayList";
    }

    @Override
    protected String unmodifiableCollectionsFactoryMethodName() {
        return "unmodifiableList";
    }

    @Override
    protected void importRequiredTypes( ClassBuilder<?> cb ) {
        cb.importing(
                AbstractList.class,
                List.class,
                ArrayList.class,
                Supplier.class,
                Collection.class,
                Comparator.class
        );
    }

    @Override
    public void generateRequiredOverrides( ClassBuilder<String> cb ) {
        generateOverride( "get", memberType, cb, "int" );
        generateOverride( "size", "int", cb );
    }

    @Override
    public void generateMutationOverrides( ClassBuilder<String> cb ) {
        generateOverride( addCheck( "arg1" ), "add", null, cb, "int", memberType );
        generateOverride( removeIntCheck(), "remove", memberType, cb, "int" );
        generateOverride( addAllCheck( "arg1" ), "addAll", "boolean", cb,
                "int", "Collection<? extends " + memberType + ">" );

        generateOverride( setCheck( "arg1" ), "set", memberType, cb, "int", memberType );
        generateOverride( "sort", null, cb, "Comparator<? super " + memberType + ">" );
    }

    @Override
    protected void generatePrimitiveArrayConversion( JavaTypes type, String sizeVariable, String resultVariable,
            ClassBuilder<String> cb,
            ClassBuilder.BlockBuilder<?> bb ) {
        bb.forVar( "i" ).condition().isLessThan( sizeVariable )
                .initializedWith( 0 ).running( loopBody -> {
            loopBody.assign( resultVariable + "[i]" )
                    .toInvocation( "get" )
                    .withArgument( "i" )
                    .on( CONTENT_FIELD );
        } );
    }

    private ConstraintCheck setCheck( String varName ) {
        return ( cb, bb ) -> {
            if ( hasMemberChecks() ) {
                invokeMemberCheck( varName, bb );
            }
        };
    }
}
