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
