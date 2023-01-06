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
package com.telenav.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorAnnotator;
import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.Modifier;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Creates one constructor.
 *
 * @author Tim Boudreau
 */
final class ConstructorCreator implements StructureContributor {

    private final List<? extends ConstructorArgumentWrapper<?>> arguments;
    private final List<? extends ConstructorAnnotator> constructorAnnotators;
    private final List<? extends DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs;
    private final boolean publicConstructor;
    private final ConstructorKind kind;

    ConstructorCreator(ConstructorKind kind, boolean publicConstructor, List<? extends ConstructorArgumentWrapper<?>> arguments, List<? extends ConstructorAnnotator> constructorAnnotators, List<? extends DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs) {
        this.arguments = arguments;
        this.publicConstructor = publicConstructor;
        this.constructorAnnotators = constructorAnnotators;
        this.docs = docs;
        this.kind = kind;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        cb.constructor(con -> {
            if (publicConstructor) {
                con.setModifier(Modifier.PUBLIC);
            } else {
                con.setModifier(Modifier.PRIVATE);
            }
            DocumentationContributor.document(helper, docs).ifPresent(dox -> {
                try {
                    con.docComment(dox);
                } catch (IllegalStateException ex) {
                    System.err.println("Error documenting " + helper.structure().getId().getName() + " with " + dox);
                    // ignore
                    ex.printStackTrace();
                }
            });
            for (ConstructorAnnotator anno : constructorAnnotators) {
                anno.apply(con, cb, kind, helper);
            }
            for (ConstructorArgumentWrapper<?> arg : arguments) {
                arg.generateArguments(con, cb, kind);
            }
            con.body(bb -> {
                List<ConstructorArgumentWrapper<?>> copy = new ArrayList<>(arguments);
                // Sort arguments by weight, so any range/pattern/length checks that
                // can fail will be run against the least expensive types first (i.e.
                // number range checks, etc.).
                Collections.sort(copy);
                for (ConstructorArgumentWrapper<?> arg : copy) {
                    arg.generateChecks(bb, cb, kind, helper);
                }
                for (ConstructorArgumentWrapper<?> arg : arguments) {
                    arg.generateAssignments(bb, cb, kind, helper);
                }
            });
        });
    }

}
