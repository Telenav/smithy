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

import com.mastfrog.function.state.Bool;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import com.mastfrog.smithy.java.generators.util.JavaSymbolProvider;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import static com.mastfrog.smithy.java.generators.util.TypeNames.typeNameOf;
import com.mastfrog.smithy.simple.extensions.AuthenticatedTrait;
import com.mastfrog.util.strings.Strings;
import static com.telenav.smithy.simple.server.generator.OperationGenerator.ensureGraphs;
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
import software.amazon.smithy.model.shapes.ShapeType;

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
        Set<Shape> allOps = rg.filteredClosure(shape, sh -> sh.getType() == ShapeType.OPERATION);

        Set<String> mechanisms = new TreeSet<>();
        Bool hasOptional = Bool.create();
        Map<OperationShape, AuthenticatedTrait> authTraitForOperation = new HashMap<>();
        Map<ShapeId, Set<OperationShape>> operationsForPayload = new HashMap<>();
        Set<ShapeId> allPayloadTypes = new HashSet<>();

        for (Shape opId : allOps) {
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
        String tn = "AuthenticateWith" + TypeNames.typeNameOf(sid);
        ClassBuilder<String> cb = ClassBuilder.forPackage(pkg)
                .named(tn).withModifier(PUBLIC)
                .annotatedWith("FunctionalInterface").closeAnnotation()
                .docComment("Autheticate requests that expect a " + TypeNames.typeNameOf(sid) + " as the result")
                .toInterface();

        cb.importing("com.mastfrog.smithy.http.SmithyRequest",
                "com.mastfrog.smithy.http.AuthenticationResultConsumer");

//        cb.importing(CompletableFuture.class);
        String mockName = cb.className() + "Mock";
        cb.importing("com.google.inject.ImplementedBy")
                .annotatedWith("ImplementedBy")
                .addClassArgument("value", mockName)
                .closeAnnotation();
        Shape payloadShape = model.expectShape(sid);
        String name = names().typeNameOf(cb, payloadShape, true);
        String ppkg = names().packageOf(payloadShape);
        maybeImport(cb, ppkg + "." + name);
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
            mb.addArgument(serviceTypeName + "AuthenticatedOperations", "target");
            if (mechanisms.size() > 1) {
                mb.addArgument(serviceTypeName + "AuthenticationMechanism", "mechanism");
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
                        "com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.AuthenticationResultConsumer")
                .importing(CompletableFuture.class);
        maybeImport(mock, ppkg + "." + name);
        mock.overridePublic("authenticate", mb -> {
            mb.addArgument(serviceTypeName + "AuthenticatedOperations", "target");
            if (mechanisms.size() > 1) {
                mb.addArgument(serviceTypeName + "AuthenticationMechanism", "mechanism");
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
                String converted = enumConstantFor(typeNameOf(op));
                String dox = "The operation " + typeNameOf(op) + " which uses <code>"
                        + authTraitForOperation.get(op).getMechanism()
                        + "</code> for authentication.";
                ecb.add(converted, dox);
            }
        });
        Set<String> optional = new TreeSet<>();
        Set<String> nonOptional = new TreeSet<>();
        for (OperationShape op : opShapesSorted) {
            String converted = enumConstantFor(typeNameOf(op));
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
                                String converted = enumConstantFor(typeNameOf(op));
                                AuthenticatedTrait atr = authTraitForOperation.get(op);
                                String tn = names().typeNameOf(cb, model.expectShape(atr.getPayload()), true);
                                String ppkg = names().packageOf(model.expectShape(atr.getPayload()));
                                maybeImport(cb, ppkg + "." + tn);
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
                .named(serviceTypeName + "AuthenticationMechanism")
                .withModifier(PUBLIC)
                .docComment("Authentication mechanisms used by operations in the " + serviceTypeName
                        + " service.")
                .toEnum();
        Function<String, Set<String>> definedBy = (mechanism) -> {
            Set<String> result = new TreeSet<>();
            authTraitForOperation.forEach((shape, auth) -> {
                if (auth.getMechanism().toLowerCase().equals(mechanism)) {
                    result.add(TypeNames.typeNameOf(shape));
                }
            });
            return result;
        };
        cb.enumConstants(cnsts -> {
            for (String mech : mechanisms) {
                String constName = enumConstantFor(mech);
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

    static String authPackage(ServiceShape shape, TypeNames names) {
        return names.packageOf(shape) + ".auth";
    }

    static String enumConstantFor(String s) {
        return JavaSymbolProvider.escape(Strings.camelCaseToDelimited(s, '_').toUpperCase());
    }

}
