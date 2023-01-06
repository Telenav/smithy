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
