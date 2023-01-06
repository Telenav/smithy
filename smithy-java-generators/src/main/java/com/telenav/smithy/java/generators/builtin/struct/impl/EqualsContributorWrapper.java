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
import com.telenav.smithy.java.generators.builtin.struct.EqualsContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class EqualsContributorWrapper<S extends Shape> {

    private final EqualsContributor<? super S> eq;
    private final StructureMember<S> member;

    EqualsContributorWrapper(StructureMember<S> member, EqualsContributor<? super S> eq) {
        this.eq = eq;
        this.member = member;
    }

    <B> void generate(String varName, StructureGenerationHelper helper, ClassBuilder.BlockBuilder<B> bb, ClassBuilder<?> cb) {
        bb.lineComment(member.member().getId() + " equals computation cost " + member.weight());
        eq.contributeToEqualsComputation(member, helper, varName, bb, cb);
    }

}
