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
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
class IdentityHashCodeGenerator implements StructureContributor {

    private final List<? extends StructureIdentityExtensionJava.HashCodeWrapper<?>> contributors;
    private final Set<StructureMember<?>> members;

    public IdentityHashCodeGenerator(Set<StructureMember<?>> members, List<? extends StructureIdentityExtensionJava.HashCodeWrapper<?>> contributors) {
        this.contributors = contributors;
        this.members = members;
    }

    private String javadoc() {
        StringBuilder sb = new StringBuilder("Overridden to compute the hash code only from  " + "the designated @identity members in the model:<ul>");
        for (StructureMember<?> sm : members) {
            sb.append("\n<li>").append(sm.jsonName()).append(" as returned by the method <code>").append(sm.getterName()).append("()</code></li>");
        }
        sb.append("</ul>\n@return whether or not the two objects are equal");
        return sb.toString();
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        for (StructureMember<?> sm : members) {
            helper.maybeImport(cb, sm.qualifiedTypeName());
        }
        String hashVar = cb.unusedFieldName("localHash");
        long prime = AbstractJavaGenerator.prime(helper.structure().getId().toString());
        cb.overridePublic("hashCode", mth -> {
            mth.docComment(javadoc());
            mth.returning("int").body(bb -> {
                if (contributors.isEmpty()) {
                    bb.returning(prime);
                } else {
                    bb.declare(hashVar).initializedTo(prime);
                    for (StructureIdentityExtensionJava.HashCodeWrapper<?> hc : contributors) {
                        hc.generate(bb, hashVar, cb);
                    }
                }
                bb.returning(ClassBuilder.variable(hashVar).xor(ClassBuilder.variable(hashVar).rotate(32).parenthesized()).castToInt());
            });
        });
    }

}
