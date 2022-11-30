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
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
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
