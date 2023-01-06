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
import java.util.ArrayList;
import static java.util.Collections.unmodifiableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * General-purpose trait for providing valid and invalid samples for use by
 * generated test code.
 *
 * @author Tim Boudreau
 */
public final class SamplesTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("com.telenav.smithy#samples");
    private final List<Node> validNodes;
    private final List<Node> invalidNodes;

    public SamplesTrait(List<Node> validNodes, List<Node> invalidNodes, SourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.validNodes = validNodes;
        this.invalidNodes = invalidNodes;
    }

    public List<Node> validSamples() {
        return unmodifiableList(validNodes);
    }

    public List<Node> invalidShapes() {
        return unmodifiableList(invalidNodes);
    }

    public <T> List<T> validSamples(Function<Node, T> f) {
        List<T> result = new ArrayList<>(validNodes.size());
        validNodes.forEach(nd -> {
            T obj = f.apply(nd);
            if (obj != null) {
                result.add(obj);
            }
        });
        return result;
    }

    public <T> List<T> invalidSamples(Function<Node, T> f) {
        List<T> result = new ArrayList<>(invalidNodes.size());
        invalidNodes.forEach(nd -> {
            T obj = f.apply(nd);
            if (obj != null) {
                result.add(obj);
            }
        });
        return result;
    }

    @Override
    protected Node createNode() {
        Map<StringNode, Node> nodes = new LinkedHashMap<>();
        ArrayNode valids = new ArrayNode(validNodes, getSourceLocation());
        ArrayNode invalids = new ArrayNode(invalidNodes, getSourceLocation());
        nodes.put(new StringNode("valid", getSourceLocation()), valids);
        nodes.put(new StringNode("invalid", getSourceLocation()), invalids);
        return new ObjectNode(nodes, getSourceLocation());
    }

    @ServiceProvider(TraitService.class)
    public static class Provider implements TraitService {

        @Override
        public ShapeId getShapeId() {
            return ID;
        }

        @Override
        public Trait createTrait(ShapeId si, Node node) {
            ObjectNode on = node.expectObjectNode();
            Optional<Node> valids = on.getMember("valid");
            Optional<Node> invalids = on.getMember("invalid");
            if (!valids.isPresent() && !invalids.isPresent()) {
                throw new ExpectationNotMetException(
                        "@samples must contain at least some samples - "
                        + "either (or both of) an array named `valid` "
                        + "and/or one named `invalid` must be present", node.getSourceLocation());
            }
            List<Node> validNodes = new ArrayList<>();
            List<Node> invalidNodes = new ArrayList<>();
            valids.ifPresent(v -> {
                ArrayNode an = v.expectArrayNode("Valid samples should be an array");
                validNodes.addAll(an.getElements());

            });
            invalids.ifPresent(v -> {
                ArrayNode an = v.expectArrayNode("Invalid samples should be an array");
                invalidNodes.addAll(an.getElements());
            });
            if (validNodes.isEmpty() && invalidNodes.isEmpty()) {
                throw new ExpectationNotMetException(
                        "Either valid or invalid example array must contain "
                        + "at least one element", node.getSourceLocation());
            }
            return new SamplesTrait(validNodes, invalidNodes, node.getSourceLocation());
        }

    }

}
