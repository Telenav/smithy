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
package com.telenav.smithy.simple.server.generator;

import com.mastfrog.function.TriConsumer;
import com.mastfrog.graph.*;
import static com.telenav.smithy.simple.server.generator.RelationTag.ERROR_FOR_OPERATION;
import static com.telenav.smithy.simple.server.generator.RelationTag.INPUT_FOR_OPERATION;
import static com.telenav.smithy.simple.server.generator.RelationTag.MEMBER_OF_SHAPE;
import static com.telenav.smithy.simple.server.generator.RelationTag.OPERATION_FOR_RESOURCE;
import static com.telenav.smithy.simple.server.generator.RelationTag.OUTPUT_FOR_OPERATION;
import static com.telenav.smithy.simple.server.generator.RelationTag.RESOURCE_FOR_RESOURCE;
import static com.telenav.smithy.simple.server.generator.RelationTag.RESOURCE_FOR_SERVICE;
import static com.telenav.smithy.simple.server.generator.RelationTag.TARGET_OF_MEMBER;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A traversable graph of all relationships in the model that can supply
 * closures of types. This allows us to perform simple lookups that find the
 * closure of all operations referenced via a service and the owning service of
 * an operation and similar, without complicated one-off code to find these
 * things for each case where something like that is needed.
 *
 * @author Tim Boudreau
 */
final class ResourceGraph {

    private final List<ShapeId> allIds;
    private final Map<ShapeId, Shape> shapeForId;
    private final Map<EdgeKey, RelationTag> tagForEdge;
    private final IntGraph ig;
    private final ObjectGraph<ShapeId> graph;

    private ResourceGraph(List<ShapeId> allIds, Map<ShapeId, Shape> shapeForId,
            Map<EdgeKey, RelationTag> tagForEdge, IntGraph ig,
            ObjectGraph<ShapeId> graph) {
        this.allIds = allIds;
        this.shapeForId = shapeForId;
        this.tagForEdge = tagForEdge;
        this.ig = ig;
        this.graph = graph;
    }

    public Set<Shape> children(Shape shape) {
        return new ConvertingSet(graph.children(shape.getId()));
    }

    public Set<Shape> parents(Shape shape) {
        return new ConvertingSet(graph.parents(shape.getId()));
    }

    public Set<Shape> closure(Shape shape) {
        return new ConvertingSet(graph.closureOf(shape.getId()));
    }

    public Set<Shape> filteredClosure(Shape shape, Predicate<Shape> test) {
        Set<Shape> result = new HashSet<>();
        closure(shape).forEach(sh -> {
            if (test.test(sh)) {
                result.add(sh);
            }
        });
        return result;
    }
    
    public <T> Set<T> transformedClosure(Shape shape, Function<Shape, T> xform) {
        Set<T> result = new HashSet<>();
        closure(shape).forEach(sh -> {
            T obj = xform.apply(sh);
            if (obj != null) {
                result.add(obj);
            }
        });
        return result;
    }

    public Set<Shape> filteredReverseClosure(Shape shape, Predicate<Shape> test) {
        Set<Shape> result = new HashSet<>();
        reverseClosure(shape).forEach(sh -> {
            if (test.test(sh)) {
                result.add(sh);
            }
        });
        return result;
    }

    public ServiceShape serviceForOperation(OperationShape op) {
        Set<Shape> shapes = filteredReverseClosure(op, shp -> shp.isServiceShape());
//        shapes.addAll(filteredClosure(op, shp -> shp.isServiceShape()));
        if (shapes.isEmpty()) {
            throw new IllegalStateException(op + " is not in the reverse closure of any service: "
                + reverseClosure(op));
        }
        return shapes.iterator().next().asServiceShape().get();
    }

    public Set<Shape> reverseClosure(Shape shape) {
        return new ConvertingSet(graph.reverseClosureOf(shape.getId()));
    }

    public boolean contains(Shape shape) {
        return shapeForId.containsKey(shape.getId());
    }

    public Optional<RelationTag> relation(Shape a, Shape b) {
        if (a == null || b == null) {
            return Optional.empty();
        }
        return relation(a.getId(), b.getId());
    }

    public Optional<RelationTag> relation(ShapeId a, ShapeId b) {
        int ixa = allIds.indexOf(a);
        int ixb = allIds.indexOf(b);
        EdgeKey ek = new EdgeKey(ixa, ixb);
        return Optional.ofNullable(tagForEdge.get(ek));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        graph.walk(new ObjectGraphVisitor<>() {
            private final LinkedList<Shape> curr = new LinkedList<>();

            @Override
            public void enterNode(ShapeId node, int depth) {
                Shape shape = shapeForId.get(node);
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                if (depth > 0) {
                    char[] c = new char[2 * depth];
                    Arrays.fill(c, ' ');
                    sb.append(c);
                }
                sb.append(shape.getType()).append(' ');
                relation(curr.peek(), shape).ifPresent(rel -> {
                    sb.append(rel).append(' ');
                });
                sb.append(shape.getId());
                curr.push(shape);
            }

            @Override
            public void exitNode(ShapeId node, int depth) {
                if (!curr.isEmpty()) {
                    curr.pop();
                }
            }

        });
        return sb.toString();
    }

    private static class EdgeKey {

        private final int src;
        private final int target;

        EdgeKey(int src, int target) {
            this.src = src;
            this.target = target;
        }

        boolean contains(int val) {
            return src == val || target == val;
        }

        boolean is(EdgeKey other) {
            if (src == other.src && target == other.target) {
                return true;
            } else if (src == other.target && target == other.src) {
                return true;
            }
            return false;
        }

        public boolean equals(Object o) {
            if (o == this) {
                return true;
            } else if (o == null || o.getClass() != EdgeKey.class) {
                return false;
            }
            EdgeKey ek = (EdgeKey) o;
            return is(ek);
        }

        public int hashCode() {
            return (src + 1) * (target + 1);
        }

        int source() {
            return src;
        }

        int target() {
            return target;
        }

        public String toString() {
            return src + ":" + target;
        }
    }

    static ResourceGraph create(ServiceShape service, Model model) {
        List<ShapeId> allIds = new ArrayList<>();
        Map<ShapeId, Shape> shapeForId = new HashMap<>();
        Map<EdgeKey, RelationTag> tagForEdge = new HashMap<>();

        IntGraphBuilder bldr = IntGraph.builder();
        shapeForId.put(service.getId(), service);
        ToIntFunction<ShapeId> indexSupplier = shape -> {
            int result = allIds.indexOf(shape);
            if (result < 0) {
                result = allIds.size();
                allIds.add(shape);
            }
            return result;
        };
        TriConsumer<Shape, Shape, RelationTag> addToGraph = (from, to, tag) -> {
            int fix = indexSupplier.applyAsInt(from.getId());
            int tix = indexSupplier.applyAsInt(to.getId());
            bldr.addEdge(fix, tix);
            shapeForId.put(from.getId(), from);
            shapeForId.put(to.getId(), to);
            tagForEdge.put(new EdgeKey(fix, tix), tag);
        };
        class ResourceConsumer<S extends Shape> implements BiConsumer<S, RelationTag> {

            private final Shape parent;

            ResourceConsumer(Shape parent) {
                this.parent = parent;
            }

            @Override
            public void accept(S t, RelationTag tag) {
                addToGraph.accept(parent, t, tag);
                if (t.getType() == ShapeType.RESOURCE) {
                    ResourceShape rs = t.asResourceShape().get();
                    ResourceConsumer<ResourceShape> sub
                            = new ResourceConsumer<>(rs);
                    rs.getIntroducedResources().forEach(iid -> {
                        sub.accept(model.expectShape(iid, ResourceShape.class), RESOURCE_FOR_RESOURCE);
                    });
                    ResourceConsumer<OperationShape> opsSub
                            = new ResourceConsumer<>(rs);

                    rs.getAllOperations().forEach(opid -> {
                        opsSub.accept(model.expectShape(opid, OperationShape.class), RelationTag.operationRelation(rs, opid));
                    });
                } else if (t.getType() == ShapeType.OPERATION) {
                    OperationShape op = t.asOperationShape().get();
                    ResourceConsumer<Shape> io = new ResourceConsumer<>(op);
                    io.accept(model.expectShape(op.getInputShape()), INPUT_FOR_OPERATION);
                    io.accept(model.expectShape(op.getOutputShape()), OUTPUT_FOR_OPERATION);
                    op.getIntroducedErrors().forEach(shp -> {
                        io.accept(model.expectShape(shp), ERROR_FOR_OPERATION);
                    });
                } else {
                    ResourceConsumer<Shape> members = new ResourceConsumer<Shape>(t);
                    t.getAllMembers().forEach((name, member) -> {
                        members.accept(member, MEMBER_OF_SHAPE);
                        Shape targ = model.expectShape(member.getTarget());
                        addToGraph.accept(member, targ, TARGET_OF_MEMBER);
                    });
                }
            }
        }
        ResourceConsumer<ResourceShape> outers = new ResourceConsumer<>(service);
        service.getIntroducedResources().forEach(res -> {
            ResourceShape shp = model.expectShape(res, ResourceShape.class);
            outers.accept(shp, RESOURCE_FOR_SERVICE);
        });
        ResourceConsumer<OperationShape> outerOps = new ResourceConsumer<>(service);
        service.getOperations().forEach(op -> {
            OperationShape shp = model.expectShape(op, OperationShape.class);
            outerOps.accept(shp, OPERATION_FOR_RESOURCE);
        });

        IntGraph ig = bldr.build();
        ObjectGraph<ShapeId> graph = ig.toObjectGraph(allIds);
        return new ResourceGraph(allIds, shapeForId, tagForEdge, ig, graph);
    }

    class ConvertingSet extends AbstractSet<Shape> {

        private final Set<ShapeId> ids;

        public ConvertingSet(Set<ShapeId> ids) {
            this.ids = ids;
        }

        @Override
        public Iterator<Shape> iterator() {
            return new Iter(ids.iterator());
        }

        @Override
        public int size() {
            return ids.size();
        }

        class Iter implements Iterator<Shape> {

            private final Iterator<ShapeId> ids;

            public Iter(Iterator<ShapeId> ids) {
                this.ids = ids;
            }

            @Override
            public boolean hasNext() {
                return ids.hasNext();
            }

            @Override
            public Shape next() {
                return shapeForId.get(ids.next());
            }
        }

    }

}
