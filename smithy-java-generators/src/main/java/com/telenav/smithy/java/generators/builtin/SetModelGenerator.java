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
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.names.JavaTypes;
import static com.telenav.smithy.names.JavaTypes.find;
import static com.telenav.smithy.names.NumberKind.forShape;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;

/**
 *
 * @author Tim Boudreau
 */
final class SetModelGenerator<S extends ListShape> extends AbstractListAndSetGenerator<S> {

    SetModelGenerator( S shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language ) {
        super( shape, model, destSourceRoot, target, language );
    }

    @Override
    protected String type() {
        return "Set";
    }

    private boolean canUseTreeSet() {
        JavaTypes tp = find( realMember );
        return tp != null && tp.isPrimitiveCapable() && forShape( realMember ) != null;
    }

    @Override
    protected boolean internalTypeTakesSizeArgument() {
        return !canUseTreeSet();
    }

    @Override
    protected String internalType() {
        if ( canUseTreeSet() ) {
            return "TreeSet";
        }
        return "LinkedHashSet";
    }

    @Override
    protected String abstractType() {
        return "AbstractSet";
    }

    @Override
    protected String unmodifiableCollectionsFactoryMethodName() {
        return "unmodifiableSet";
    }

    @Override
    protected void importRequiredTypes( ClassBuilder<?> cb ) {
        cb.importing(
                AbstractSet.class,
                Set.class,
                Supplier.class,
                Collection.class,
                Iterator.class
        );
        if ( canUseTreeSet() ) {
            cb.importing( TreeSet.class );
        } else {
            cb.importing( LinkedHashSet.class );
        }
//        cb.generateDebugLogCode();
    }

    @Override
    protected void generateRequiredOverrides( ClassBuilder<String> cb ) {
        generateOverride( "size", "int", cb );
        if ( hasMinSize() ) {
            generateIterator( cb );
        } else {
            generateOverride( "iterator", "Iterator<" + memberType() + ">", cb );
        }
    }

    @Override
    public void generateInstanceMethods( ClassBuilder<String> cb ) {
        super.generateInstanceMethods( cb );
        cb.importing( List.class, ArrayList.class );
        cb.method( "asList", mth -> {
            mth.withModifier( PUBLIC )
                    .docComment( "Create a copy of this " + cb.className() + " as a "
                            + "<code>List&lt;" + memberType + "&gt;</code>."
                            + "\n@return a list containing the contents of this instance" )
                    .returning( "List<" + memberType + ">" )
                    .body( bb -> {
                        bb.returningNew( nb -> {
                            nb.withArgument( CONTENT_FIELD )
                                    .ofType( "ArrayList<>" );
                        } );
                    } );
        } );
    }

    @Override
    protected void generateMutationOverrides( ClassBuilder<String> cb ) {
        // the general ones cover all the cases
    }

    protected void generateIterator( ClassBuilder<String> cb ) {
        String iterClassName = cb.className() + memberType() + "Iterator";
        cb.innerClass(iterClassName, iter -> {
            iter.withModifier( PRIVATE, FINAL )
                    .docComment( "Iterator whose remove() method ensures we cannot go below"
                            + " the minimum size constraint on this class." )
                    .implementing( "Iterator<" + memberType() + ">" )
                    .field( "delegate", del -> {
                        del.withModifier( PRIVATE, FINAL )
                                .ofType( "Iterator<? extends " + memberType() + ">" );
                    } )
                    .constructor( con -> {
                        con.setModifier( PRIVATE )
                                .addArgument( "Iterator<? extends " + memberType() + ">", "del" );
                        con.body( bb -> {
                            bb.assign( "this.delegate" ).toExpression( "del" );
                        } );
                    } )
                    .overridePublic( "hasNext", hasNext -> {
                        hasNext.returning( "boolean" );
                        hasNext.body( bb -> {
                            bb.returningInvocationOf( "hasNext" ).on( "delegate" );
                        } );
                    } )
                    .overridePublic( "next", next -> {
                        next.returning( memberType() );
                        next.body( bb -> {
                            bb.returningInvocationOf( "next" ).on( "delegate" );
                        } );
                    } )
                    .overridePublic("remove", remove -> {
                        remove.body(bb -> {
                            bb.lineComment( "We need to ensure we do not go below the size constraint," );
                            bb.lineComment( "which is the reason this class exists at all." );
                            IfBuilder<?> test = bb.iff().booleanExpression( "size() == MIN_SIZE" );
                            validationExceptions().createThrow( iter, test,
                                    "Removing an element would cause the iterated " + cb.className()
                                    + "to be below the minimum size in its schema of ", "MIN_SIZE" );
                            test.endIf();
                            bb.invoke( "remove" ).on( "delegate" );
                        } );
                    } )
                    .overridePublic( "forEachRemaining", forEachRemaining -> {
                        // For efficiency, we might as well override this
                        forEachRemaining.addArgument( "Consumer<? super " + memberType + ">", "consumer" )
                                .body( bb -> {
                                    bb.invoke( "forEachRemaining" )
                                            .withArgument( "consumer" )
                                            .on( "delegate" );
                                } );
                    } );
        } );
        cb.overridePublic( "iterator" )
                .returning( "Iterator<" + memberType() + ">" )
                .body( bb -> {
                    bb.lineComment( "Returns a custom iterator which ensures that the" )
                            .lineComment( "iterator's remove method cannot violate the minimum" )
                            .lineComment( "size constraint of " + minSize )
                            .returningNew( nb -> {
                                nb.withArgumentFromInvoking( "iterator" )
                                        .on( CONTENT_FIELD )
                                        .ofType( iterClassName );
                            } );
                } );
    }
}
