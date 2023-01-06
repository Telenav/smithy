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
package com.telenav.smithy.simple.server.generator;

import com.mastfrog.function.state.Bool;
import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import com.telenav.smithy.extensions.AuthenticatedTrait;
import static com.telenav.smithy.names.TypeNames.enumConstantName;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.telenav.smithy.names.operation.OperationNames;
import static com.telenav.smithy.names.operation.OperationNames.authPackage;
import static com.telenav.smithy.names.operation.OperationNames.serviceAuthenticatedOperationsEnumName;
import static com.telenav.smithy.names.operation.OperationNames.serviceAuthenticationMechanismTypeName;
import static com.telenav.smithy.simple.server.generator.OperationGenerator.ensureGraphs;
import com.telenav.smithy.utils.ResourceGraph;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;

/**
 *
 * @author Tim Boudreau
 */
final class ServiceOperationAuthGenerator extends AbstractJavaGenerator<ServiceShape> {

    ServiceOperationAuthGenerator(ServiceShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ResourceGraph rg = ensureGraphs(model, shape);
        Set<Shape> ops = rg.filteredClosure(shape, sh -> sh.getType() == OPERATION);

        Set<String> mechanisms = new TreeSet<>();
        Bool hasOptional = Bool.create();
        Map<OperationShape, AuthenticatedTrait> authTraitForOperation = new HashMap<>();
        Map<ShapeId, Set<OperationShape>> operationsForPayload = new HashMap<>();
        Set<ShapeId> allPayloadTypes = new HashSet<>();

        for (Shape opId : ops) {
            OperationShape op = opId.asOperationShape().get();
            op.getTrait(AuthenticatedTrait.class).ifPresent(authTrait -> {
                mechanisms.add(authTrait.getMechanism().toLowerCase());
                hasOptional.or(authTrait::isOptional);
                authTraitForOperation.put(op, authTrait);
                allPayloadTypes.add(authTrait.getPayload());
                operationsForPayload.computeIfAbsent(authTrait.getPayload(), p -> new HashSet<>()).add(op);
            });
        }
        if (mechanisms.isEmpty()) {
            return;
        }
        String serviceTypeName = typeNameOf(shape);
        String pkg = authPackage(shape, names());
        if (mechanisms.size() > 1) {
            addTo.accept(generateMechanismsEnum(pkg, serviceTypeName, authTraitForOperation, mechanisms));
        }
        addTo.accept(generateOperationsEnum(pkg, serviceTypeName, authTraitForOperation));

        for (ShapeId sid : allPayloadTypes) {
            generateAuthenticationInterfaceAndMockImplementation(operationsForPayload, sid, pkg, mechanisms, serviceTypeName, addTo);
        }
    }

    private void generateAuthenticationInterfaceAndMockImplementation(Map<ShapeId, Set<OperationShape>> operationsForPayload, ShapeId sid, String pkg, Set<String> mechanisms, String serviceTypeName, Consumer<ClassBuilder<String>> addTo) {
        Set<OperationShape> ops = operationsForPayload.get(sid);
        String tn = OperationNames.authenticateWithInterfaceName(sid);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(tn).withModifier(PUBLIC)
                .annotatedWith("FunctionalInterface").closeAnnotation()
                .docComment("Autheticate requests that expect a " + typeNameOf(sid) + " as the result")
                .toInterface();

        cb.importing("com.telenav.smithy.http.SmithyRequest",
                "com.telenav.smithy.http.AuthenticationResultConsumer");

        String mockName = cb.className() + "Mock";
        cb.importing("com.google.inject.ImplementedBy")
                .annotatedWith("ImplementedBy")
                .addClassArgument("value", mockName)
                .closeAnnotation();
        Shape payloadShape = model.expectShape(sid);
        String name = names().typeNameOf(cb, payloadShape, true);
        String ppkg = names().packageOf(payloadShape);
        String[] fqns = new String[]{ppkg + "." + name};
        maybeImport(cb, fqns);
        cb.method("authenticate", mb -> {
            mb.docComment("Authenticate a request - this method effectively translates the inbound "
                    + "SmithyRequest into an instance of <code>" + typeNameOf(payloadShape) + "</code>, "
                    + "and should complete the passed future exceptionally if the request cannot be "
                    + "authenticated.  If authentication is <i>optional</i> (in the trait declaration in the model), "
                    + "then the passed future may be completed normally with <code>null</code> to indicate that."
                    + "\nThe passed future <b><i>must</i></b> be called in all cases; it may (and likely should) be called "
                    + "asynchronously if something such as database I/O is required to perform lookups required to "
                    + "authenticate the request and it is possible to do that asynchronously."
                    + "\n@param target The operation the request applies to"
                    + (mechanisms.size() > 1 ? "\n@param mechanism The authentication mechanism of schema, such as BASIC" : "")
                    + "\n@param request The request"
                    + "\n@param optional Whether or not authentication is optional for this operation"
                    + "\n@param results A callback to populate with the result of authentication which <b><i>must</i></b> be called");
            mb.addArgument(serviceAuthenticatedOperationsEnumName(shape), "target");
            if (mechanisms.size() > 1) {
                mb.addArgument(serviceAuthenticatedOperationsEnumName(shape), "mechanism");
            }
            mb.addArgument("SmithyRequest", "request");
            mb.addArgument("boolean", "optional");
            mb.addArgument("AuthenticationResultConsumer<" + name + ">", "results");
        });
        addTo.accept(cb);
        ClassBuilder<String> mock = ClassBuilder.forPackage(cb.packageName())
                .named(mockName)
                .implementing(cb.className())
                .withModifier(FINAL)
                .importing(
                        "com.telenav.smithy.http.SmithyRequest",
                        "com.telenav.smithy.http.AuthenticationResultConsumer")
                .importing(CompletableFuture.class);
        String[] fqns1 = new String[]{ppkg + "." + name};
        maybeImport(mock, fqns1);
        mock.overridePublic("authenticate", mb -> {
            mb.addArgument(serviceAuthenticatedOperationsEnumName(shape), "target");
            if (mechanisms.size() > 1) {
                mb.addArgument(serviceAuthenticationMechanismTypeName(shape), "mechanism");
            }
            mb.addArgument("SmithyRequest", "request");
            mb.addArgument("boolean", "optional");
            mb.addArgument("AuthenticationResultConsumer<" + name + ">", "results");
            mb.body(bb -> {
                bb.invoke("failed")
                        .withArgumentFromNew(nb -> {
                            nb.withStringConcatentationArgument("No implementation of ")
                                    .append(cb.className())
                                    .append(" was bound.  Authentication is not implemented.")
                                    .endConcatenation()
                                    .ofType("UnsupportedOperationException");
                        }).on("results");
            });
        });
        addTo.accept(mock);
    }

    private ClassBuilder<String> generateOperationsEnum(String pkg, String serviceTypeName,
            Map<OperationShape, AuthenticatedTrait> authTraitForOperation) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(serviceTypeName + "AuthenticatedOperations")
                .docComment("Operations which use authentication in " + serviceTypeName + ".")
                .withModifier(PUBLIC)
                .toEnum();
        Set<OperationShape> opShapesSorted
                = new TreeSet<>((a, b) -> typeNameOf(a).compareToIgnoreCase(typeNameOf(b)));
        opShapesSorted.addAll(authTraitForOperation.keySet());
        cb.enumConstants(ecb -> {
            for (OperationShape op : opShapesSorted) {
                String converted = enumConstantName(typeNameOf(op));
                String dox = "The operation " + typeNameOf(op) + " which uses <code>"
                        + authTraitForOperation.get(op).getMechanism()
                        + "</code> for authentication.";
                ecb.add(converted, dox);
            }
        });
        Set<String> optional = new TreeSet<>();
        Set<String> nonOptional = new TreeSet<>();
        for (OperationShape op : opShapesSorted) {
            String converted = enumConstantName(typeNameOf(op));
            if (authTraitForOperation.get(op).isOptional()) {
                optional.add(converted);
            } else {
                nonOptional.add(converted);
            }
        }
        cb.method("isAuthenticationOptional", mth -> {
            mth.withModifier(PUBLIC)
                    .returning("boolean")
                    .body(bb -> {
                        bb.switchingOn("this", sw -> {
//                            for (String s : optional) {
//                                sw.inCase(s, cs -> cs.returning(true));
//                            }
                            if (!optional.isEmpty()) {
                                sw.inCases(cs -> cs.returning(true), optional.toArray(String[]::new));
                            }
                            if (!nonOptional.isEmpty()) {
                                sw.inCases(cs -> cs.returning(false), nonOptional.toArray(String[]::new));
                            }
//                            for (String s : nonOptional) {
//                                sw.inCase(s, cs -> cs.returning(true));
//                            }
                            sw.inDefaultCase(cs -> {
                                cs.andThrow(nb -> {
                                    nb.withArgument("this")
                                            .ofType("AssertionError");
                                });
                            });
                        });
                    });
        });
        cb.method("authenticationPayloadType", mth -> {
            mth.returning("Class<?>")
                    .withModifier(PUBLIC)
                    .body(bb -> {
                        bb.switchingOn("this", sw -> {
                            for (OperationShape op : opShapesSorted) {
                                String converted = enumConstantName(typeNameOf(op));
                                AuthenticatedTrait atr = authTraitForOperation.get(op);
                                String tn = names().typeNameOf(cb, model.expectShape(atr.getPayload()), true);
                                String ppkg = names().packageOf(model.expectShape(atr.getPayload()));
                                String[] fqns = new String[]{ppkg + "." + tn};
                                maybeImport(cb, fqns);
                                sw.inCase(converted, cs -> {
                                    cs.returning(tn + ".class");
                                });
                            }
                            sw.inDefaultCase(cs -> cs.andThrow(nb -> {
                                nb.withArgument("this")
                                        .ofType("AssertionError");
                            }));
                        });
                    });
        });
        return cb;
    }

    private ClassBuilder<String> generateMechanismsEnum(String pkg,
            String serviceTypeName, Map<OperationShape, AuthenticatedTrait> authTraitForOperation,
            Set<String> mechanisms) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(serviceAuthenticationMechanismTypeName(shape))
                .withModifier(PUBLIC)
                .docComment("Authentication mechanisms used by operations in the " + serviceTypeName
                        + " service.")
                .toEnum();
        Function<String, Set<String>> definedBy = (mechanism) -> {
            Set<String> result = new TreeSet<>();
            authTraitForOperation.forEach((shape, auth) -> {
                if (auth.getMechanism().toLowerCase().equals(mechanism)) {
                    result.add(typeNameOf(shape));
                }
            });
            return result;
        };
        cb.enumConstants(cnsts -> {
            for (String mech : mechanisms) {
                String constName = enumConstantName(mech);
                StringBuilder dox = new StringBuilder(
                        "Authentication mechanism used by operations:<ul>");
                definedBy.apply(mech).forEach(name -> {
                    dox.append("<li>").append(name).append("</li>");
                });
                dox.append("</ul>");
                cnsts.add(constName, dox.toString());
            }
        });
        return cb;
    }
}
