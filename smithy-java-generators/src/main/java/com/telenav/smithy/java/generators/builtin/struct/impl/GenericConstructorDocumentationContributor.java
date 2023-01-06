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

import com.telenav.smithy.java.generators.builtin.struct.ConstructorKind;
import com.telenav.smithy.java.generators.builtin.struct.DocumentationContributor;
import com.telenav.smithy.java.generators.builtin.struct.StructureGenerationHelper;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 *
 * @author Tim Boudreau
 */
final class GenericConstructorDocumentationContributor implements DocumentationContributor<StructureShape, StructureGenerationHelper> {

    private final ConstructorKind kind;

    GenericConstructorDocumentationContributor(ConstructorKind kind) {
        this.kind = kind;
    }

    @Override
    public void generateDocumentation(StructureGenerationHelper target, StringBuilder docHead, StringBuilder docTail) {
        switch (kind) {
            case JSON_DESERIALIZATON:
                docHead.append("Creates a new ").append(target.structure().getId().getName()).append('.');
                break;
            case SECONDARY_WITH_CONVENIENCE_INTS_OR_DOUBLES:
                docHead.append("Convenience constructor for ").append(target.structure().getId().getName()).append(" which substitutes primitive ints or doubles for bytes, shorts or floats, for ").append(target.structure().getId().getName()).append('.');
                break;
            case SECONDARY_WITH_PRIMITIVES:
                docHead.append("Convenience constructor for ").append(target.structure().getId().getName()).append(" which takes primitives, for").append(target.structure().getId().getName()).append('.');
        }
    }

}
