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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.SourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.TraitService;

/**
 * Marks one or more fields of a structure as contributing to the identity (for
 * equals and hash code purposes) of the type.
 *
 * @author Tim Boudreau
 */
public final class IdentityTrait extends AnnotationTrait {

    public static final String NAMESPACE = "com.telenav.smithy";

    public static final ShapeId ID = ShapeId.from(NAMESPACE + "#identity");

    public IdentityTrait(ShapeId id, ObjectNode node) {
        super(id, node);
    }

    public IdentityTrait(ObjectNode node) {
        super(ID, node);
    }

    public IdentityTrait() {
        this(Node.objectNode());
    }

    public IdentityTrait(SourceLocation sourceLocation) {
        this(new ObjectNode(Collections.emptyMap(), sourceLocation));
    }

    @ServiceProvider(TraitService.class)
    public static final class Provider extends AnnotationTrait.Provider<IdentityTrait> {

        public Provider() {
            super(ID, IdentityTrait::new);
        }

        @Override
        public IdentityTrait createTrait(ShapeId id, Node value) {
            return super.createTrait(id, value);
        }
    }

    static String n2s(Node nd) {
        StringBuilder sb = new StringBuilder();
        n2s(nd, sb, 0);
        return sb.toString();
    }

    private static void n2s(Node node, StringBuilder sb, int depth) {
        char[] c = new char[depth * 2];
        Arrays.fill(c, ' ');
        sb.append('\n').append(c);
        sb.append(node.getType()).append(' ');
        switch (node.getType()) {
            case OBJECT: {
                node.asObjectNode().ifPresent(on -> {
                    sb.append('(').append(on.getMembers().size()).append(')');
                    on.getMembers().forEach((name, nd) -> {
                        sb.append(name).append(":");
                        n2s(nd, sb, depth + 1);
                    });
                });
                break;
            }
            case NUMBER:
                sb.append(node.asNumberNode().get().getValue());
                break;
            case BOOLEAN:
                sb.append(node.asBooleanNode().get().getValue());
                break;
            case STRING:
                sb.append('"').append(node.asStringNode().get().getValue()).append('"');
                break;
            case NULL:
                sb.append("null");
                break;
            case ARRAY:
                node.asArrayNode().ifPresent(an -> {
                    List<Node> els = an.getElements();
                    sb.append('(').append(els.size()).append(')');
                    for (int i = 0; i < els.size(); i++) {
                        Node n = els.get(i);
                        sb.append('\n').append(c).append("  ").append(i);
                        n2s(n, sb, depth + 1);
                    }
                });
                break;
            default:
                throw new AssertionError(node.getType());
        }
    }

}
