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
import com.telenav.smithy.java.generators.builtin.struct.HeadTailToStringContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
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
