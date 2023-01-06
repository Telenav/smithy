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
