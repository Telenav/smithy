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
package com.mastfrog.smithy.java.generators.builtin.struct.impl;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Generates an isEmpty() method, only on structures whose fields are all
 * nullable.
 *
 * @author Tim Boudreau
 */
final class IsEmptyMethodGenerator implements StructureContributor {

    static final IsEmptyMethodGenerator INSTANCE = new IsEmptyMethodGenerator();

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        
        List<StructureMember<?>> mems = helper.members();
        // Possible but unusual - we will have nothing to test on in this case
        if (mems.isEmpty()) {
            return;
        }
        // See if any members are required, in which case we do not generate
        for (StructureMember<?> mem : helper.members()) {
            if (mem.isRequired()) {
                return;
            }
            if (mem.hasDefault()) {
                return;
            }
        }
        // Make sure some other plugin is not already generating a conflicting
        // method and/or we are not being run twice
        if (!cb.containsMethodNamed("isEmpty")) { // collision-proofing
            cb.importing("com.fasterxml.jackson.annotation.JsonIgnore");
            // Create our check method
            cb.method("isEmpty", mth -> {
                mth.returning("boolean")
                        .annotatedWith("JsonIgnore").closeAnnotation()
                        .withModifier(PUBLIC)
                        .docComment(cb.className() + " has no members that are marked as "
                                + "<i>required</i>, so an empty instance (all members are null) is "
                                + "possible.  This method will return true for such an instance."
                                + "\n@return true if all fields of this " + cb.className() + " are null")
                        .body(bb -> {
                            Value v = null;
                            for (StructureMember mem : mems) {
                                Value fieldTest = variable(mem.field()).isNotNull();
                                if (v == null) {
                                    v = fieldTest;
                                } else {
                                    v = v.logicalOrWith(fieldTest);
                                }
                            }
                            bb.iff(v).returning(false).endIf();
                            bb.returning(true);
                        });
            });
        }
        // Also create an empty-instance field
        if (!cb.containsFieldNamed("EMPTY")) { // collision-proofing
            cb.field("EMPTY", fld -> {
                fld.withModifier(PUBLIC, STATIC, FINAL)
                        .annotatedWith("JsonIgnore").closeAnnotation()
                        .docComment("The empty instance - a " + cb.className()
                                + " with all null fields.")
                        .initializedWithNew(nb -> {
                            for (int i = 0; i < mems.size(); i++) {
                                nb = nb.withArgument("null");
                            }
                            nb.ofType(cb.className());
                        }).ofType(cb.className());
            });
        }
    }

}
