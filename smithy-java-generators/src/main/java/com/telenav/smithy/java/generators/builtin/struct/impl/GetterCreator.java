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
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.GetterDecorator;
import com.telenav.smithy.java.generators.builtin.struct.GetterGenerator;
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import com.telenav.smithy.java.generators.builtin.struct.StructureMember;
import java.util.Collection;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class GetterCreator<S extends Shape> implements StructureContributor {

    private final StructureMember<? extends S> member;
    private final Collection<? extends GetterDecorator<? super S>> annotators;
    private final Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors;
    private final GetterGenerator<S> generator;

    GetterCreator(StructureMember<? extends S> member, GetterGenerator<S> generator, Collection<? extends GetterDecorator<? super S>> annotators, Collection<? extends DocumentationContributor<? super S, StructureMember<? extends S>>> docContributors) {
        this.member = member;
        this.annotators = annotators;
        this.docContributors = docContributors;
        this.generator = generator;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        generator.generateGetter(member, annotators, docContributors, cb);
    }

}
