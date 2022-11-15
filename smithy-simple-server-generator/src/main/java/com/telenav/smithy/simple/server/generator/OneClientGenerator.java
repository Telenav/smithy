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

import com.mastfrog.java.vogon.ClassBuilder;
import static com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator.decapitalize;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 *
 * @author Tim Boudreau
 */
public class OneClientGenerator {

    private static final String CLIENT_BASE_PKG = "com.mastfrog.smithy.client.base";
    private final Model model;
    private final TypeNames typeNames;

    public OneClientGenerator(Model model) {
        this.model = model;
        this.typeNames = new TypeNames(model);
    }

    private static String clientBaseClass(String str) {
        return CLIENT_BASE_PKG + "." + str;
    }

    public void generateClient(ServiceShape service, Consumer<ClassBuilder<String>> c) {
        String clientName = service.getId().getName();
        String className = clientName + "Client";
        ClassBuilder<String> client = ClassBuilder.forPackage(typeNames.packageOf(service) + ".client")
                .named(className)
                .withModifier(PUBLIC, FINAL)
                .extending("BaseServiceClient<" + className + ">")
                .importing(CompletableFuture.class)
                .importing(clientBaseClass("BaseServiceClient"),
                        "com.mastfrog.smithy.client.result.ServiceResult"
                ).constructor(con -> {
                    // String apiName, String version, String defaultEndpoint
                    con.setModifier(PUBLIC)
                            .body(bb -> {
                                bb.invoke("super")
                                        .withStringLiteral(clientName)
                                        .withStringLiteral(service.getVersion())
                                        .withStringLiteral("http://localhost:8123")
                                        .inScope();
                            });
                })
                .constructor(con -> {
                    con.addArgument("String", "endpoint")
                            .setModifier(PUBLIC)
                            .body(bb
                                    -> bb.invoke("super")
                                    .withStringLiteral(clientName)
                                    .withStringLiteral(service.getVersion())
                                    .withArgument("endpoint")
                                    .inScope()
                            );
                })
                .overridePublic("withEndpoint", mth
                        -> mth.returning(className)
                        .addArgument("String", "endpoint")
                        .body(bb -> bb
                        .returningNew(
                                nb -> nb.withArgument("endpoint")
                                        .ofType(className))));

        for (ShapeId res : service.getResources()) {
            ResourceShape shp = model.expectShape(res, ResourceShape.class);
            generateForOneResource(service, client, shp);
        }
        c.accept(client);
    }

    private void generateForOneResource(ServiceShape belongsTo, ClassBuilder<String> into, ResourceShape resourceShape) {

        String urlBase = "/" + belongsTo.getId().getName() + "/v" + belongsTo.getVersion() + "/";

        Set<ShapeId> used = new HashSet<>();
        resourceShape.getRead().ifPresent(readShapeId -> {
            used.add(readShapeId);
            OperationShape shape = (OperationShape) model.expectShape(readShapeId);
            createOneOperationMethod(ResourceLifecycleOperationKind.READ, shape, resourceShape, belongsTo, urlBase, into);
        });

        resourceShape.getUpdate().ifPresent(updateShapeId -> {
            used.add(updateShapeId);
            OperationShape shape = (OperationShape) model.expectShape(updateShapeId);
            createOneOperationMethod(ResourceLifecycleOperationKind.UPDATE, shape, resourceShape, belongsTo, urlBase, into);
        });

        resourceShape.getCreate().ifPresent(createShapeId -> {
            used.add(createShapeId);
            OperationShape shape = (OperationShape) model.expectShape(createShapeId);
            createOneOperationMethod(ResourceLifecycleOperationKind.CREATE, shape, resourceShape, belongsTo, urlBase, into);
        });

        resourceShape.getDelete().ifPresent(deleteShapeId -> {
            used.add(deleteShapeId);
            OperationShape shape = (OperationShape) model.expectShape(deleteShapeId);
            createOneOperationMethod(ResourceLifecycleOperationKind.DELETE, shape, resourceShape, belongsTo, urlBase, into);
        });

        resourceShape.getOperations().forEach(op -> {
            if (!used.contains(op)) {
                OperationShape shape = (OperationShape) model.expectShape(op);
                createOneOperationMethod(ResourceLifecycleOperationKind.OTHER, shape, resourceShape, belongsTo, urlBase, into);
            }
        });
    }

    private void createOneOperationMethod(ResourceLifecycleOperationKind resourceLifecycleOperationKind,
            OperationShape shape, ResourceShape resourceShape, ServiceShape belongsTo,
            String urlBase, ClassBuilder<String> into) {

        String urlPath = urlBase + shape.getId().getName();
        String mname;
        if (resourceLifecycleOperationKind == ResourceLifecycleOperationKind.OTHER) {
            mname = decapitalize(shape.getId().getName());
        } else {
            mname = resourceLifecycleOperationKind.name().toLowerCase()
                    + shape.getId().getName();
        }

        Optional<ShapeId> inp = shape.getInput();
        Shape inputShape;
        if (!inp.isPresent()) {
            inputShape = null;
        } else {
            inputShape = model.expectShape(inp.get());
        }

        Optional<ShapeId> outp = shape.getOutput();
        Shape outputShape;
        if (!outp.isPresent()) {
            outputShape = null;
        } else {
            outputShape = model.expectShape(outp.get());
        }

        into.method(mname, mth -> {
            mth.withModifier(PUBLIC);
            String inShapeType;
            String outShapeType;
            if (inputShape != null) {
                inShapeType = typeNames.typeNameOf(into, inputShape, true);
                String inShapeFqn = typeNames.packageOf(inputShape) + "." + inShapeType;
                into.importing(inShapeFqn);
                mth.addArgument(inShapeType, "input");
            } else {
                inShapeType = null;
            }
            if (outputShape != null) {
                outShapeType = typeNames.typeNameOf(into, outputShape, true);
                String outShapeFqn = typeNames.packageOf(outputShape) + "." + outShapeType;
                into.importing(outShapeFqn);
                mth.returning("CompletableFuture<ServiceResult<" + outShapeType + ">>");
            } else {
                outShapeType = null;
                mth.returning("CompletableFuture<ServiceResult<Void>>");
            }
            String verb = resourceLifecycleOperationKind.verb(shape).toLowerCase();
            mth.body(bb -> {
                ClassBuilder.InvocationBuilder<?> invocation = bb.returningInvocationOf(verb);

                if (inShapeType != null) {
                    invocation.withArgument("input");
                }
                String outClass = outShapeType == null ? "Void"
                        : outShapeType;
                invocation.withStringLiteral(urlPath)
                        .withClassArgument(outClass)
                        .on("super");
            });
        });
    }

    public enum ResourceLifecycleOperationKind {
        CREATE("PUT"),
        READ("POST"), // XXX this actually depends on if everything is in the query/url
        UPDATE("POST"),
        DELETE("DELETE"),
        LIST("GET"),
        OTHER("OTHER");
        private final String verb;

        ResourceLifecycleOperationKind(String verb) {
            this.verb = verb;
        }

        String verb(OperationShape shape) {
            if (this == OTHER) {
                // XXX look up trait
                return "POST";
            }
            return verb;
        }

        public String acteurPrefix(ResourceShape resource, OperationShape op) {
            switch (this) {
                case CREATE:
                    return "Create";
                case READ:
                    return "Read";
                case UPDATE:
                    return "Update";
                case DELETE:
                    return "Delete";
                case LIST:
                    return "List";
                case OTHER:
                    return op.getId().getName();
                default:
                    throw new AssertionError(this);
            }
        }

        @Override
        public String toString() {
            return verb;
        }

    }

}
