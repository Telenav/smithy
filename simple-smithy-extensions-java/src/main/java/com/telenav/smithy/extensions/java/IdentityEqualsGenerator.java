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
package com.telenav.smithy.extensions.java;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.java.generators.builtin.struct.EqualsContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.spi.StructureExtensions;
import java.util.Set;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
class IdentityEqualsGenerator implements StructureContributor {

    private final Set<StructureMember<?>> members;

    public IdentityEqualsGenerator(Set<StructureMember<?>> members) {
        this.members = members;
    }

    private String javadoc() {
        StringBuilder sb = new StringBuilder("Overridden to test equality only on " + "the designated @identity members in the model:<ul>");
        for (StructureMember<?> sm : members) {
            sb.append("\n<li>").append(sm.jsonName()).append(" as returned by the method <code>").append(sm.getterName()).append("()</code></li>");
        }
        sb.append("</ul>\n@return whether or not the two objects are equal");
        return sb.toString();
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        cb.overridePublic("equals", mth -> {
            mth.addArgument("Object", "o").returning("boolean").docComment(javadoc()).body(bb -> {
                String otherVar = helper.generateInitialEqualsTest(cb, bb);
                for (StructureMember<?> sm : members) {
                    oneEqualsTest(sm, helper, bb, cb, otherVar);
                }
                bb.returning(true);
            });
        });
    }

    <T, S extends Shape> void oneEqualsTest(StructureMember<S> sm, StructureGenerationHelper helper, ClassBuilder.BlockBuilder<?> bb, ClassBuilder<T> cb, String otherVar) {
        helper.context().get(StructureExtensions.KEY).get().collectEqualsContributors(helper, sm, (EqualsContributor<? super S> eq) -> {
            eq.contributeToEqualsComputation(sm, helper, otherVar, bb, cb);
        });
    }

}
