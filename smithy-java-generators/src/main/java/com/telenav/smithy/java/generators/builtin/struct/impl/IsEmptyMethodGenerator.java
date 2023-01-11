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
import com.mastfrog.java.vogon.ClassBuilder.Value;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
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
                            for (StructureMember<?> mem : mems) {
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
