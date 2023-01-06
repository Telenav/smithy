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
import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.TraitService;

/**
 *
 * @author Tim Boudreau
 */
public class SpanTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#span");
    private final String lesser;
    private final String greater;
    private final boolean emptyAllowed;

    public SpanTrait(String lesser, String greater, boolean emptyAllowed, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.lesser = lesser;
        this.greater = greater;
        this.emptyAllowed = emptyAllowed;
    }

    public String lesser() {
        return lesser;
    }

    public String greater() {
        return greater;
    }

    public boolean emptyAllowed() {
        return emptyAllowed;
    }

    @Override
    protected Node createNode() {
        Map<StringNode, Node> map = new HashMap<>(2);
        map.put(StringNode.from("lesser"), StringNode.from(lesser));
        map.put(StringNode.from("greater"), StringNode.from(greater));
        map.put(StringNode.from("emptyAllowed"), BooleanNode.from(emptyAllowed));
        return new ObjectNode(map, getSourceLocation());
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AbstractTrait.Provider {

        public Provider() {
            super(ID);
        }

        @Override
        public SpanTrait createTrait(ShapeId target, Node value) {
            ObjectNode nd = value.expectObjectNode();
            String lesser = nd.expectStringMember("lesser").getValue();
            String greater = nd.expectStringMember("greater").getValue();
            boolean emptyAllowed = nd.getBooleanMember("emptyAllowed").orElse(BooleanNode.from(true)).getValue();
            return new SpanTrait(lesser, greater, emptyAllowed, value.getSourceLocation());
        }
    }
}
