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
