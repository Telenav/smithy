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
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
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
public final class AuthenticatedTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#authenticated");
    private static final String DEFAULT_STYLE = "basic";
    private final String mechanism;
    private final boolean optional;
    private final ShapeId payload;

    /**
     *
     * @param mechanism
     * @param optional
     * @param payload
     * @param sourceLocation
     */
    public AuthenticatedTrait(String mechanism, boolean optional, ShapeId payload, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.mechanism = mechanism;
        this.optional = optional;
        this.payload = payload;
    }

    public AuthenticatedTrait(String value) {
        this(value, false, ShapeId.from("smithy.api#String"), SourceLocation.NONE);
    }

    public AuthenticatedTrait(String value, boolean optional) {
        this(value, optional, ShapeId.from("smithy.api#String"), SourceLocation.NONE);
    }

    public ShapeId getPayload() {
        return payload;
    }

    public String getMechanism() {
        return mechanism;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    protected Node createNode() {
        Map<StringNode, Node> map = new HashMap<>(2);
        map.put(new StringNode("mechanism", getSourceLocation()), new StringNode(mechanism, getSourceLocation()));
        map.put(new StringNode("optional", getSourceLocation()), new BooleanNode(optional, getSourceLocation()));
        return new ObjectNode(map, getSourceLocation());
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
                    return new AuthenticatedTrait(DEFAULT_STYLE);
                case STRING:
                    return new AuthenticatedTrait(node.expectStringNode().getValue());
                case OBJECT:
                    ObjectNode on = node.expectObjectNode();
                    String mech = on.getStringMember("mechanism")
                            .map(sn -> sn.getValue()).orElse("basic");
                    boolean optional = on.getBooleanMemberOrDefault("optional", false);

                    ShapeId sid = on.getStringMember("payload")
                            .map(p -> ShapeId.from(p.getValue())).orElse(ShapeId.from("smithy.api#String"));
                    return new AuthenticatedTrait(mech, optional, sid, node.getSourceLocation());
                default:
                    throw new ExpectationNotMetException("@authenticated takes either a single string or nothing",
                            node.getSourceLocation());
            }
        }
    }
}
