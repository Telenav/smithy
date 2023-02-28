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
import com.mastfrog.function.state.Obj;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.telenav.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.telenav.smithy.server.common.Declaration;
import com.telenav.smithy.server.common.DeclarationClose;
import com.telenav.smithy.server.common.Declarer;
import com.telenav.smithy.server.common.Input;
import com.telenav.smithy.server.common.InputMemberObtentionStrategy;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToBigDecimal;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToBigInteger;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToBoolean;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToIntEnum;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToNewFromString;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.mapToTimestamp;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.originMethod;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.originMethodCastTo;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.splitToMappedCollection;
import static com.telenav.smithy.server.common.InvocationBuilderTransform.splitToStringSet;
import com.telenav.smithy.server.common.OriginType;
import static com.telenav.smithy.server.common.OriginType.HTTP_HEADER;
import static com.telenav.smithy.server.common.OriginType.URI_PATH;
import static com.telenav.smithy.server.common.OriginType.URI_QUERY;
import com.telenav.smithy.server.common.PayloadOrigin;
import com.telenav.smithy.server.common.RequestParameterOrigin;
import com.telenav.smithy.java.generators.auth.AuthUtils;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.enumConstantName;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.names.operation.OperationNames.authPackage;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceFqn;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceName;
import static com.telenav.smithy.simple.server.generator.SmithyServerGenerator.maybeBuildGraph;
import com.telenav.smithy.utils.ResourceGraph;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.smithy.utils.ShapeUtils.requiredOrHasDefault;
import com.telenav.smithy.utils.path.PathInformationExtractor;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern.Segment;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.SERVICE;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.CorsTrait;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
final class OperationGenerator extends AbstractJavaGenerator<OperationShape> {

    static boolean graphsBuilt;
    private final ResourceGraph graph;
    private final AuthUtils auth;
    private ActeurRequestIdSupport requestIdSupport;

    OperationGenerator(OperationShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
        auth = new AuthUtils(model, shape);
        graph = ensureGraphs(model);
    }

    ResourceGraph ensureGraphs(Model model) {
        return ensureGraphs(model, shape);
    }

    static ResourceGraph ensureGraphs(Model model, Shape sh) {
        Obj<ResourceGraph> result = Obj.create();
        if (!graphsBuilt) {
            model.shapes().forEach(shape -> shape.asServiceShape().ifPresent(service -> {
                ResourceGraph grp = maybeBuildGraph(service, model);
                if (grp != null && grp.contains(shape)) {
                    result.set(grp);
                }
            }));
        }
        result.ifUnset(() -> {
            result.set(SmithyServerGenerator.graph(sh));
        });
        return result.get();
    }

    String implPackage() {
        String s = packageOf(names().packageOf(shape));
        return s + ".serverimpl";
    }

    private String service() {
        for (Shape sh : graph.reverseClosure(shape)) {
            if (sh.getType() == SERVICE) {
                return sh.getId().getName();
            }
        }
        return null;
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder sb = new StringBuilder("Implements the " + shape.getId().getName()
                + " operation defined in <code>" + shape.getId() + "</code>.");

        shape.getInput().ifPresent(in -> {
            Input inp = examineInput(model.expectShape(in, StructureShape.class), graph,
                    ClassBuilder.forPackage("dummy").named("X"));
            if (!inp.isEmpty()) {
                sb.append("\n<h2>Input</h2>");
                sb.append("Input to the operation is of the type <code>{@link ")
                        .append(inp.typeName()).append("}</code>. It is computed "
                        + "from: ");
                sb.append("<ul>");
                inp.forEach(inputObtentionStrategy -> {
                    inputObtentionStrategy.visit((origin, shape, memberShape) -> {
                        sb.append("<li><b>");
                        sb.append(origin.type()).append(' ')
                                .append("<code>")
                                .append(origin.qualifier())
                                .append("</code>");
                        sb.append("</b> converted to a <code>");
                        sb.append(typeNameOf(shape));
                        sb.append("</code> as specified by the member ");
                        sb.append("<code>").append(memberShape.getMemberName()).append("</code> ");
                        sb.append(" of ").append(inp.typeName()).append("</code>");
                        sb.append(" (<i><code>").append(memberShape.getId()).append("</code></i>)");
                        sb.append("</li>");
                    });
                });
                sb.append("</ul>");
            }
        });

        return sb.toString();
    }

    private String generateAuthPrecursor(ClassBuilder<String> owner, Shape payload,
            String mechanism, String pkg, String payloadType,
            boolean optional, Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(owner.packageName())
                .named(owner.className() + "Preauthenticator")
                .importing(
                        "com.mastfrog.acteur.Acteur",
                        "javax.inject.Inject",
                        "com.mastfrog.util.function.EnhCompletableFuture",
                        "com.telenav.smithy.http.AuthenticationResultConsumer",
                        pkg + "." + payloadType,
                        "com.telenav.smithy.http.SmithyRequest")
                .withModifier(FINAL)
                .extending("Acteur");

        ServiceShape service = graph.serviceForOperation(shape);
        String authPkg = authPackage(service, names());
        String authenticatorInterfaceName = "AuthenticateWith" + payloadType;
        cb.importing(authPkg + "." + authenticatorInterfaceName);

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument("SmithyRequest", "request");
            con.addArgument(authenticatorInterfaceName, "authenticator");
            con.body(bb -> {
                bb.declare("future")
                        .initializedByInvoking("defer")
                        .inScope()
                        .as("EnhCompletableFuture<" + payloadType + ">");
                String enumConstantName = enumConstantName(shape.getId().getName());
                String enumConstantType = typeNameOf(service) + "AuthenticatedOperations";
                cb.importing(authPkg + "." + enumConstantType);
                bb.invoke("authenticate")
                        .withArgumentFromField(enumConstantName).of(enumConstantType)
                        .withArgument("request")
                        .withArgument(optional)
                        //                        .withArgument("future")
                        .withArgumentFromInvoking("create")
                        .withArgument("future")
                        .withArgument(optional)
                        .on("AuthenticationResultConsumer")
                        .on("authenticator");

                bb.invoke("next").inScope();
            });
        });
        addTo.accept(cb);
        return cb.className();
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        requestIdSupport = new ActeurRequestIdSupport(ctx, addTo);
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage())
                .named(typeNameOf(shape))
                .withModifier(FINAL);
        cb.importing("javax.inject.Inject",
                "com.mastfrog.acteur.Acteur")
                .extending("Acteur");
        applyGeneratedAnnotation(OperationGenerator.class, cb);
        generateDocumentationAndDocumentationAnnotations(cb);

        ConstructorBuilder<ClassBuilder<String>> con = cb.constructor()
                .annotatedWith("Inject").closeAnnotation();

        if (!shape.getInput().isPresent()) {
            con.addArgument(operationInterfaceName(shape), "operationImplementation");
            cb.importing(operationInterfaceFqn(model, shape));
        }

        ServiceShape service = graph.serviceForOperation(shape);

        service.getTrait(CorsTrait.class).ifPresent(cors -> {
            cb.importing("com.mastfrog.acteur.preconditions.CORS");
            cb.annotatedWith("CORS", ab -> {
                Set<String> headers = new TreeSet<>(cors.getAdditionalAllowedHeaders());
                headers.addAll(cors.getAdditionalExposedHeaders());
                if (cors.getMaxAge() > 0) {
                    ab.addArgument("maxAgeSeconds", cors.getMaxAge());
                }
                ab.addArgument("origins", cors.getOrigin());
            });
        });

        boolean hasAuth = auth.withAuthInfo((Shape payload, String mechanism, String pkg, String payloadType, boolean optional) -> {
            String[] fqns = new String[]{pkg + "." + payloadType};
            maybeImport(cb, fqns);
            if (optional) {
                cb.importing("javax.inject.Provider");
                con.addArgument("Provider<" + payloadType + ">", "authInfo");
            } else {
                con.addArgument(payloadType, "authInfo");
            }
            cb.importing("com.mastfrog.acteur.annotations.Precursors");
            String authActeur = generateAuthPrecursor(cb, payload, mechanism, pkg, payloadType, optional, addTo);
            cb.annotatedWith("Precursors", ab -> {
                ab.addClassArgument("value", authActeur);
            });
        });

        requestIdSupport.generateRequestInjectionCode(cb, null, "--x--", hasAuth);

        BlockBuilder<ClassBuilder<String>> conBody = con.body();

        Optional<Input> in = shape.getInput().map(input -> {
            Input inp = examineInput(model.expectShape(input, StructureShape.class), graph, cb);
            return inp.apply(cb, con, conBody);
        });
        if (!in.isPresent() || in.get().isEmpty()) {
            cb.docComment("Accepts the HTTP payload (if any) as input and invokes the SPI.");
            cb.importing("com.telenav.smithy.http.SmithyRequest");
            con.addArgument("SmithyRequest", "request");
            if (in.isPresent()) {
                cb.importing(in.get().fqn());
                cb.importing("com.mastfrog.acteur.preconditions.InjectRequestBodyAs");
                cb.annotatedWith("InjectRequestBodyAs", anno -> {
                    anno.addClassArgument("value", in.get().typeName());
                });
            }
            con.throwing("Exception");
            generateDeferralCode(cb, true, con, conBody);
        } else {
            cb.docComment("Computes the final input shape from elements of the HTTP request and"
                    + "injects them into the scope for use by {@link " + cb.className() + "Concluder}.");
//            if (!in.isPresent()) {
//                String iface = operationInterfaceName(shape);
//                cb.importing(operationInterfaceFqn(model, shape));
//                con.addArgument(iface, "operationImplementation");
//            }
            addTo.accept(generatePostActeur(cb, in.get()));
        }

        conBody.endBlock();

        addTo.accept(cb);
    }

    private void generateDocumentationAndDocumentationAnnotations(ClassBuilder<String> cb) {
        applyDocumentation(cb);

        cb.importing("com.mastfrog.acteur.preconditions.Description");
        shape.getTrait(DocumentationTrait.class).ifPresentOrElse(dox -> {
            cb.annotatedWith("Description", anno -> {
                anno.addArgument("value", dox.getValue()
                        + "\n" + additionalDocumentation());
                String sv = service();
                if (sv != null) {
                    anno.addArgument("category", sv);
                }
            });
        }, () -> {
            cb.annotatedWith("Description", anno -> {
                anno.addArgument("value", additionalDocumentation());
                String sv = service();
                if (sv != null) {
                    anno.addArgument("category", sv);
                }
            });
        });
    }

    <T> ClassBuilder<String> generatePostActeur(ClassBuilder<T> orig, Input in) {
        String docComment = "Applies the implementation of the SPI interface for "
                + shape.getId() + " to the input computed in " + orig.className() + ".";
        ClassBuilder<String> cb = ClassBuilder.forPackage(orig.packageName())
                .named(orig.className() + "Concluder")
                .docComment(docComment)
                .withModifier(FINAL);
        applyGeneratedAnnotation(OperationGenerator.class, cb);

        orig.importing("com.mastfrog.acteur.annotations.Concluders");
        orig.annotatedWith("Concluders", conc -> {
            conc.addClassArgument("value", cb.className());
        });

        String ifaceName = operationInterfaceName(shape());
        String ifaceFqn = operationInterfaceFqn(model(), shape());
        cb.importing("com.mastfrog.acteur.preconditions.Description")
                .annotatedWith("Description").withValue(docComment);
        cb.importing(ifaceFqn).extending("Acteur");
        ConstructorBuilder<ClassBuilder<String>> con = cb.constructor()
                .annotatedWith("Inject").closeAnnotation()
                .throwing("Exception");
        cb.importing(
                "com.telenav.smithy.http.SmithyRequest",
                "com.telenav.smithy.http.SmithyResponse",
                "com.mastfrog.acteur.Acteur",
                "javax.inject.Inject"
        );

        auth.withAuthInfo((Shape payload, String mechanism, String pkg, String payloadType, boolean optional) -> {
            String[] fqns = new String[]{pkg + "." + payloadType};
            maybeImport(cb, fqns);
            if (optional) {
                cb.importing("javax.inject.Provider");
                con.addArgument("Provider<" + payloadType + ">", "authInfo");
            } else {
                con.addArgument(payloadType, "authInfo");
            }
        });

        con.addArgument("SmithyRequest", "request");
        if (in != null) {
            maybeImport(cb, in.fqn());
            con.addArgument(in.typeName(), "input");
        }
        con.addArgument(ifaceName, "operationImplementation");
        BlockBuilder<ClassBuilder<String>> constructorBody = con.body();
        generateDeferralCode(cb, in != null, con, constructorBody);
        constructorBody.endBlock();
        return cb;
    }

    <T> void generateDeferralCode(ClassBuilder<T> cb, boolean hasInput, ConstructorBuilder<ClassBuilder<T>> con, BlockBuilder<ClassBuilder<T>> bb) {

        cb.importing("static com.telenav.smithy.acteur.adapter.SmithyResponseAdapter.smithyResponse",
                "com.telenav.smithy.http.SmithyResponse",
                "com.telenav.smithy.http.SmithyRequest",
                "com.mastfrog.util.function.EnhCompletableFuture");

        String resultType = shape.getOutput().map(sid -> {
            Shape outputShape = model.expectShape(sid);
            // ensure an import
            String fqn = names().qualifiedNameOf(outputShape, cb, true);
            cb.importing(fqn);
            return typeNameOf(outputShape);
        }).orElse("Void");

// EnhCompletableFuture<Object> fut = deferThenRespond(HttpResponseStatus.MULTI_STATUS);
        InvocationBuilder<TypeAssignment<BlockBuilder<ClassBuilder<T>>>> futureDeclaration
                = bb.declare("fut")
                        .initializedByInvoking("deferThenRespond");

        shape.getTrait(HttpTrait.class)
                .ifPresent(tr -> {
                    cb.importing("io.netty.handler.codec.http.HttpResponseStatus");
                    int code = tr.getCode();
                    switch (code) {
                        case 200:
                            futureDeclaration.withArgumentFromField("OK")
                                    .of("HttpResponseStatus");
                            break;
                        case 201:
                            futureDeclaration.withArgumentFromField("CREATED")
                                    .of("HttpResponseStatus");
                            break;
                        case 202:
                            futureDeclaration.withArgumentFromField("ACCEPTED")
                                    .of("HttpResponseStatus");
                            break;
                        case 203:
                            futureDeclaration.withArgumentFromField("NON_AUTHORITATIVE_INFORMATION")
                                    .of("HttpResponseStatus");
                        case 501:
                            futureDeclaration.withArgumentFromField("NOT_IMPLEMENTED")
                                    .of("HttpResponseStatus");
                            break;
                        default:
                            futureDeclaration.withArgumentFromInvoking("valueOf")
                                    .withArgument(code)
                                    .on("HttpResponseStatus");

                    }
                });
        futureDeclaration.inScope().as("EnhCompletableFuture<" + resultType + ">");

        bb.declare("response")
                .initializedByInvoking("smithyResponse")
                .withArgumentFromInvoking("response")
                .inScope()
                .withArgument("fut")
                .inScope()
                .as("SmithyResponse<" + resultType + ">");

        InvocationBuilder<BlockBuilder<ClassBuilder<T>>> inv
                = bb.invoke("respond").withArgument("request");
        auth.withAuthInfo((Shape payload, String mechanism, String pkg, String payloadType, boolean optional) -> {
            if (optional) {
                cb.importing(Optional.class);
                inv.withArgumentFromInvoking("ofNullable")
                        .withArgumentFromInvoking("get")
                        .on("authInfo")
                        .on("Optional");
            } else {
                inv.withArgument("authInfo");
            }
        });

        if (shape.getInput().isPresent()) {
            inv.withArgument("input");
        }

        inv.withArgument("response")
                .on("operationImplementation");
        bb.invoke("next").inScope();
    }

    private Input examineInput(StructureShape input, ResourceGraph graph,
            ClassBuilder<String> cb) {
        Optional<HttpTrait> httpOpt = shape.getTrait(HttpTrait.class);
        List<InputMemberObtentionStrategy> st = new ArrayList<>();
        if (httpOpt.isPresent()) {
            HttpTrait http = httpOpt.get();
            UriPattern pattern = http.getUri();

            new PathInformationExtractor(model, pattern)
                    .assembleActeurPathAnnotation(input, cb);
            cb.importing(
                    "com.mastfrog.acteur.headers.Method",
                    "com.mastfrog.acteur.preconditions.Methods",
                    "com.mastfrog.acteur.annotations.HttpCall"
            );
            cb.annotatedWith("Methods", ab -> {
                if ("GET".equalsIgnoreCase(http.getMethod())) {
                    ab.addArrayArgument("value", arr -> {
                        arr.expression("Method.HEAD")
                                .expression("Method.GET");
                    });
                } else {
                    ab.addExpressionArgument("value", "Method." + http.getMethod().toUpperCase());
                }
            });

            addNumericQueryParametersAnnotation(input, cb);

            for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {
                MemberShape m = e.getValue();
                Shape memberTarget = model.expectShape(m.getTarget());
                // Ensure the member type is imported
                names().typeNameOf(cb, memberTarget, false);
                if (m.getTrait(HttpPayloadTrait.class).isPresent()) {

                    Shape payloadShape = model.expectShape(m.getTarget());
                    String fqn = names().packageOf(payloadShape) + "."
                            + typeNameOf(payloadShape);
                    String[] fqns = new String[]{fqn};
                    maybeImport(cb, fqns);
                    cb.importing("com.mastfrog.acteur.preconditions.InjectRequestBodyAs");
                    cb.annotatedWith("InjectRequestBodyAs", anno -> {
                        anno.addClassArgument("value", typeNameOf(payloadShape));
                    });
                    st.add(new InputMemberObtentionStrategy(new PayloadOrigin(fqn), payloadShape, m, names()));
                } else if (m.getTrait(HttpLabelTrait.class).isPresent()) {
                    HttpLabelTrait t = m.expectTrait(HttpLabelTrait.class);
                    String name = m.getMemberName();
                    // XXX check the trait that can provide an alternate name

                    List<Segment> segs = pattern.getSegments();
                    int ix = -1;
                    for (int i = 0; i < segs.size(); i++) {
                        Segment seg = segs.get(i);
                        if (seg.isLabel()) {
                            if (seg.getContent().equals(name)) {
                                ix = i;
                                break;
                            }
                        }
                    }

                    RequestParameterOrigin uq = new RequestParameterOrigin(Integer.toString(ix), URI_PATH, declarationFor(URI_PATH, memberTarget, m, model, cb));

                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpQueryTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    RequestParameterOrigin uq = new RequestParameterOrigin(name, URI_QUERY,
                            declarationFor(URI_QUERY, memberTarget, m, model, cb));
                    // XXX check the trait that can provide an alternate name
                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpHeaderTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    HttpHeaderTrait trait = m.getTrait(HttpHeaderTrait.class).get();

                    RequestParameterOrigin uq = new RequestParameterOrigin(trait.getValue(), HTTP_HEADER,
                            declarationFor(HTTP_HEADER, memberTarget, m, model, cb));
                    // XXX check the trait that can provide an alternate name
                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                }
            }
        } else {
//            throw new UnsupportedOperationException("HTTP input inferencing not implemented yet.");
        }
//        if (st.isEmpty()) {
//            String fqn = names().packageOf(shape) + "." + TypeNames.typeNameOf(shape);
//            InputMemberObtentionStrategy strat = new InputMemberObtentionStrategy(new PayloadOrigin(fqn), shape, null);
//            st.add(strat);
//        }
        return new Input(input, st, shape, names());
    }

    private void addNumericQueryParametersAnnotation(StructureShape input, ClassBuilder<String> cb) {
        // Acteur has a ParametersMustBeNumbersIfPresent which can filter out bad requests
        // ahead of any processing on our part
        List<String> numberParameters = new ArrayList<>();
        Bool hasDecimal = Bool.create();
        Bool canBeNegative = Bool.create();
        for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {
            MemberShape m = e.getValue();
            m.getTrait(HttpQueryTrait.class).ifPresent(q -> {
                Shape ms = model.expectShape(m.getTarget());
                boolean isNumber;
                switch (ms.getType()) {
                    case INTEGER:
                    case LONG:
                    case BYTE:
                    case SHORT:
                        isNumber = true;
                        break;
                    case DOUBLE:
                    case FLOAT:
                        isNumber = true;
                        hasDecimal.set(true);
                        break;
                    default:
                        isNumber = false;
                }
                if (isNumber) {
                    numberParameters.add(q.getValue());
                    if (!canBeNegative.get()) {
                        Optional<RangeTrait> rng
                                = m.getTrait(RangeTrait.class)
                                        .or(() -> ms.getTrait(RangeTrait.class));
                        if (!rng.isPresent()) {
                            canBeNegative.set(true);
                        } else {
                            rng.get().getMin().ifPresent(val -> {
                                if (val.longValue() < 0) {
                                    canBeNegative.set(true);
                                }
                            });
                            rng.get().getMax().ifPresent(val -> {
                                if (val.longValue() < 0) {
                                    canBeNegative.set(true);
                                }
                            });
                        }
                    }
                }
            });
        }
        if (!numberParameters.isEmpty()) {
            cb.importing("com.mastfrog.acteur.preconditions.ParametersMustBeNumbersIfPresent");
            cb.annotatedWith("ParametersMustBeNumbersIfPresent", anno -> {
                if (numberParameters.size() == 1) {
                    anno.addArgument("value", numberParameters.get(0));
                } else {
                    anno.addArrayArgument("value", arr -> {
                        numberParameters.forEach(arr::literal);
                    });
                }
                if (hasDecimal.getAsBoolean()) {
                    anno.addArgument("allowDecimal", true);
                }
                if (!canBeNegative.getAsBoolean()) {
                    anno.addArgument("allowNegative", false);
                }
            });
        }
    }

    <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declaration<?, ?, ?, ?>
            declarationFor(OriginType type, Shape memberTarget, MemberShape member, Model model,
                    ClassBuilder<?> cb) {

        boolean required = member.getTrait(RequiredTrait.class).isPresent();
        Optional<DefaultTrait> def = member.getTrait(DefaultTrait.class)
                .or(() -> memberTarget.getTrait(DefaultTrait.class));
        boolean isModelType = !"smithy.api".equals(memberTarget.getId().getNamespace());

        Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> decl;
        if (def.isPresent()) {
            decl = Declarer.<B, Tr, Rr>withDefaultFor(def.get(), memberTarget, model);
        } else if (required) {
            String[] fqns = new String[]{validationExceptions().fqn()};
            maybeImport(cb, fqns);
            decl = Declarer.<B, Tr, Rr>orThrow(validationExceptions().name());
        } else {
            decl = Declarer.<B, Tr, Rr>nullable();
        }
        return applyDeclaration(decl, type, required, isModelType, memberTarget, member, model, cb);
    }

    static <B extends BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends InvocationBuilderBase<TypeAssignment<B>, Ir>>
            Declaration<B, Tr, Rr, ?>
            applyDeclaration(Declarer<B, Tr, Rr, Ir> dec, OriginType type, boolean required, boolean isModelType,
                    Shape memberTarget, MemberShape member, Model model, ClassBuilder<?> cb) {

        TypeNames tn = new TypeNames(model);
        Declarer<B, Tr, Rr, InvocationBuilder<TypeAssignment<B>>> res;
        switch (memberTarget.getType()) {
            case INTEGER:
                res = dec.with(originMethodCastTo(Integer.class));
                break;
            case LONG:
                res = dec.with(originMethodCastTo(Long.class));
                break;
            case SHORT:
                res = dec.with(originMethodCastTo(Short.class));
                break;
            case BYTE:
                res = dec.with(originMethodCastTo(Byte.class));
                break;
            case FLOAT:
                res = dec.with(originMethodCastTo(Float.class));
                break;
            case DOUBLE:
                res = dec.with(originMethodCastTo(Double.class));
                break;
            case STRING:
                String tp = tn.qualifiedNameOf(memberTarget, cb, false);
                maybeImport(cb, tp);
                res = dec.with(mapToNewFromString(
                        typeNameOf(memberTarget)))
                        .with(originMethod());
                break;
            case BIG_DECIMAL:
                res = dec.with(mapToBigDecimal())
                        .with(originMethod());
                break;
            case BIG_INTEGER:
                res = dec.with(mapToBigInteger())
                        .with(originMethod());
                break;
            case LIST:
                res = dec.with(splitToMappedCollection(memberTarget, model))
                        .with(originMethod());
                break;
            case SET:
                res = dec.with(splitToStringSet())
                        .with(originMethod());
                break;
            case BOOLEAN:
                res = dec.with(mapToBoolean())
                        .with(originMethod());
                break;
            case TIMESTAMP:
                res = dec.with(mapToTimestamp())
                        .with(originMethod());
                break;
            case INT_ENUM:
                maybeImport(cb, tn.qualifiedNameOf(memberTarget, cb, false));
                res = dec.with(mapToIntEnum(typeNameOf(memberTarget)))
                        .with(originMethod());
                break;
            default:
                throw new UnsupportedOperationException("Not implemented: " + memberTarget.getType() + " " + memberTarget);
        }
        boolean requiredOrHasDefault = requiredOrHasDefault(member, memberTarget);
        return res.closedWith(DeclarationClose.onRequest(typeNameOf(memberTarget, requiredOrHasDefault)));
    }

}
