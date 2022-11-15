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
package com.mastfrog.smithy.simple.extensions;

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
