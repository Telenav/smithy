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
