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
 * StructureContributor wrapper for a collection of DocumentationContributors.
 *
 * @author Tim Boudreau
 */
final class ClassDocumentationCreator implements StructureContributor {

    private final List<? extends DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs;

    ClassDocumentationCreator(List<? extends DocumentationContributor<? super StructureShape, ? super StructureGenerationHelper>> docs) {
        this.docs = docs;
    }

    @Override
    public <T> void generate(StructureGenerationHelper helper, ClassBuilder<T> cb) {
        DocumentationContributor.document(helper, docs).ifPresent(cb::docComment);
    }

}
