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
import com.telenav.smithy.java.generators.builtin.struct.StructureContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import java.util.List;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Aggregates a set of DocumentationContributors into a StructureContributor
 * that applies javadoc at the class level.
 */
final class ClassDocAggregator implements StructureContributor {

    private final List<? extends DocumentationContributor<StructureShape, StructureGenerationHelper>> docs;

    ClassDocAggregator(List<? extends DocumentationContributor<StructureShape, StructureGenerationHelper>> docs) {
        this.docs = docs;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        if (!docs.isEmpty()) {
            DocumentationContributor.document(helper, docs).ifPresent(cb::docComment);
        }
    }

}
