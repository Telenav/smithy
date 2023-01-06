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
import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * Mark a structure as wanting a "builder" type generated for it in languages
 * where that is appropriate.
 *
 * @author Tim Boudreau
 */
public final class BuilderTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#builder");
    private static final String DEFAULT_STYLE = "default";
    private final String value;

    public BuilderTrait(String value, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.value = value;
    }

    public BuilderTrait(String value) {
        this(value, SourceLocation.NONE);
    }

    public String getValue() {
        return value;
    }

    @Override
    protected Node createNode() {
        return new StringNode(value, getSourceLocation());
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AbstractTrait.Provider {

        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId si, Node node) {
            switch (node.getType()) {
                case NULL:
                    return new BuilderTrait(DEFAULT_STYLE);
                case STRING:
                    return new BuilderTrait(node.expectStringNode().getValue());
                default:
                    throw new ExpectationNotMetException("@builder takes either a single string or nothing",
                            node.getSourceLocation());
            }
        }
    }
}
