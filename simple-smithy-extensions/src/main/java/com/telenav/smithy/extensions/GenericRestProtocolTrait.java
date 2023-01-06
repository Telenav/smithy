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
 * Generic REST HTTP protocol trait - generating swagger demands that services
 * be marked with *some* protocol trait; and all the canned definitions in the
 * Smithy project are AWS-specific.
 *
 * @author Tim Boudreau
 */
public final class GenericRestProtocolTrait extends AnnotationTrait {

    public static final String NAMESPACE = "com.telenav.smithy";

    public static final ShapeId ID = ShapeId.from(NAMESPACE + "#genericRestProtocol");

    public GenericRestProtocolTrait(ShapeId id, ObjectNode node) {
        super(id, node);
    }

    public GenericRestProtocolTrait(ObjectNode node) {
        super(ID, node);
    }

    public GenericRestProtocolTrait() {
        this(Node.objectNode());
    }

    public GenericRestProtocolTrait(SourceLocation sourceLocation) {
        this(new ObjectNode(Collections.emptyMap(), sourceLocation));
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AnnotationTrait.Provider<GenericRestProtocolTrait> {

        public Provider() {
            super(ID, GenericRestProtocolTrait::new);
        }

        @Override
        public GenericRestProtocolTrait createTrait(ShapeId id, Node value) {
            return super.createTrait(id, value);
        }
    }
}
