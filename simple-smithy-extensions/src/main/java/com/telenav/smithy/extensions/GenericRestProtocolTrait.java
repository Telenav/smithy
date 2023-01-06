/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
