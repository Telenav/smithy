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
import com.mastfrog.smithy.java.generators.builtin.struct.HeadTailToStringContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureContributor;
import com.mastfrog.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import static java.util.Collections.emptyList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class ToStringCreator implements StructureContributor {

    private final List<? extends ToStringContributionWrapper<?>> contributors;
    private final List<? extends HeadTailToStringContributor> heads;
    private final List<? extends HeadTailToStringContributor> tails;

    ToStringCreator(List<? extends HeadTailToStringContributor> heads,
            List<? extends ToStringContributionWrapper<?>> contributors,
            List<? extends HeadTailToStringContributor> tails) {
        this.contributors = contributors;
        this.heads = heads.isEmpty() ? emptyList() : heads;
        this.tails = tails.isEmpty() ? emptyList() : tails;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        cb.overridePublic("toString", mth -> {
            mth.docComment("Implementation of Object.toString() - while not guaranteed " + "to emit valid JSON in the case of raw list, map or set members, if " + "all of the class members are generated types or primitives, it will be." + "\n@return A json-eseque string representation of this " + cb.className());
            mth.returning("String").body(bb -> {
                ClassBuilder.StringConcatenationBuilder<?> concat = bb.returningStringConcatenation();
                heads.forEach(head -> {
                    head.contributeToToString(helper, cb, concat);
                });
                concat.append("{");
                for (Iterator<? extends ToStringContributionWrapper<?>> it = contributors.iterator(); it.hasNext();) {
                    ToStringContributionWrapper<?> w = it.next();
                    w.generate(helper, cb, concat, it.hasNext());
                }
                concat.append("}");
                tails.forEach(head -> {
                    head.contributeToToString(helper, cb, concat);
                });
                concat.endConcatenation();
            });
        });
    }

}
