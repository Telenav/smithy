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
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultHashCodeGenerator implements StructureContributor {

    private final List<? extends HashCodeWrapper<?>> contributors;

    DefaultHashCodeGenerator(List<? extends HashCodeWrapper<?>> contributors) {
        this.contributors = contributors;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        String hashVar = cb.unusedFieldName("localHash");
        long prime = AbstractJavaGenerator.prime(helper.structure().getId().toString());
        cb.overridePublic("hashCode", mth -> {
            mth.returning("int").body(bb -> {
                if (contributors.isEmpty()) {
                    bb.returning(prime);
                } else {
                    bb.declare(hashVar).initializedTo(prime);
                    for (HashCodeWrapper<?> hc : contributors) {
                        hc.generate(bb, hashVar, cb);
                    }
                }
                bb.returning(ClassBuilder.variable(hashVar).xor(ClassBuilder.variable(hashVar).rotate(32).parenthesized()).castToInt());
            });
        });
    }

}
