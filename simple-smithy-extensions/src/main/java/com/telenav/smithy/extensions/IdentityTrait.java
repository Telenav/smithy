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
