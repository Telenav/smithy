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
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import com.telenav.smithy.java.generators.builtin.struct.ToStringContributor;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class ToStringContributionWrapper<S extends Shape> {

    private final StructureMember<S> member;
    private final ToStringContributor<? super S> contributor;

    ToStringContributionWrapper(StructureMember<S> member, ToStringContributor<? super S> contributor) {
        this.member = member;
        this.contributor = contributor;
    }

    <T> void generate(StructureGenerationHelper helper, ClassBuilder<?> cb, ClassBuilder.StringConcatenationBuilder<T> concat, boolean hasFollowingMembers) {
        contributor.contributeToToString(member, helper, cb, concat, hasFollowingMembers);
    }

}
