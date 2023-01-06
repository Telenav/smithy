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
