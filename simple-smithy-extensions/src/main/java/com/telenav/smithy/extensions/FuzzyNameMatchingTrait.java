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
package com.telenav.smithy.extensions;

import com.mastfrog.util.service.ServiceProvider;
import java.util.Collections;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * Trait which can be applied to an enum if JSON deserialization should match
 * permutations of enum member names that vary by case or substituting hyphens
 * for underscores.
 *
 * @author Tim Boudreau
 */
public class FuzzyNameMatchingTrait extends AnnotationTrait {

    public static final String NAMESPACE = "com.telenav.smithy";

    public static final ShapeId ID = ShapeId.from(NAMESPACE + "#fuzzyNameMatch");

    public FuzzyNameMatchingTrait(ShapeId id, ObjectNode node) {
        super(id, node);
    }

    public FuzzyNameMatchingTrait(ObjectNode node) {
        super(ID, node);
    }

    public FuzzyNameMatchingTrait() {
        this(Node.objectNode());
    }

    public FuzzyNameMatchingTrait(SourceLocation sourceLocation) {
        this(new ObjectNode(Collections.emptyMap(), sourceLocation));
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AnnotationTrait.Provider<FuzzyNameMatchingTrait> {

        public Provider() {
            super(ID, FuzzyNameMatchingTrait::new);
        }

        @Override
        public FuzzyNameMatchingTrait createTrait(ShapeId id, Node value) {
            return super.createTrait(id, value);
        }
    }

}
