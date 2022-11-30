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
package com.telenav.smithy.smithy.vertx.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.mastfrog.smithy.server.common.Declaration;
import com.mastfrog.smithy.server.common.DeclarationClose;
import com.mastfrog.smithy.server.common.Declarer;
import com.mastfrog.smithy.server.common.Input;
import com.mastfrog.smithy.server.common.InputMemberObtentionStrategy;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBigDecimal;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBigInteger;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToBoolean;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToIntEnum;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToNewFromString;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.mapToTimestamp;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.originMethod;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.originMethodCastTo;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.splitToMappedCollection;
import static com.mastfrog.smithy.server.common.InvocationBuilderTransform.splitToStringSet;
import com.mastfrog.smithy.server.common.OriginType;
import static com.mastfrog.smithy.server.common.OriginType.HTTP_HEADER;
import static com.mastfrog.smithy.server.common.OriginType.URI_PATH;
import static com.mastfrog.smithy.server.common.OriginType.URI_QUERY;
import com.mastfrog.smithy.server.common.PayloadOrigin;
import com.mastfrog.smithy.server.common.RequestParameterOrigin;
import com.mastfrog.smithy.simple.extensions.AuthenticatedTrait;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.packageOf;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.telenav.smithy.names.operation.OperationNames;
import static com.telenav.smithy.names.operation.OperationNames.authPackage;
import static com.telenav.smithy.names.operation.OperationNames.authenticateWithInterfaceName;
import static com.telenav.smithy.names.operation.OperationNames.serviceAuthenticatedOperationsEnumName;
import com.telenav.smithy.utils.ResourceGraph;
import com.telenav.smithy.utils.ResourceGraphs;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.smithy.utils.ShapeUtils.requiredOrHasDefault;
import com.telenav.smithy.utils.path.PathInfo;
import com.telenav.smithy.utils.path.PathInformationExtractor;
import static com.telenav.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.BOOLEAN;
import static software.amazon.smithy.model.shapes.ShapeType.BYTE;
import static software.amazon.smithy.model.shapes.ShapeType.DOUBLE;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import static software.amazon.smithy.model.shapes.ShapeType.INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.INT_ENUM;
import static software.amazon.smithy.model.shapes.ShapeType.LIST;
import static software.amazon.smithy.model.shapes.ShapeType.LONG;
import static software.amazon.smithy.model.shapes.ShapeType.SET;
import static software.amazon.smithy.model.shapes.ShapeType.SHORT;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import static software.amazon.smithy.model.shapes.ShapeType.TIMESTAMP;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public class VertxServerGenerator extends AbstractJavaGenerator<ServiceShape> {

    private final boolean debug;

    VertxServerGenerator(ServiceShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        super(shape, model, destSourceRoot, target, language);
        debug = settings.is(DEBUG);
    }

    private void initCb(ClassBuilder<?> cb) {
        applyGeneratedAnnotation(VertxServerGenerator.class, cb);
        if (debug) {
            cb.generateDebugLogCode();
        }
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> routerBuilder = ClassBuilder.forPackage(names().packageOf(shape))
                .named(escape(shape.getId().getName(shape)))
                .docComment("Generates and can start the " + shape.getId().getName() + ".")
                .withModifier(PUBLIC, FINAL)
                .importing(
                        "com.telenav.vertx.guice.VertxGuiceModule",
                        "com.telenav.vertx.guice.verticle.VerticleBuilder",
                        "io.vertx.core.http.HttpMethod",
                        "com.mastfrog.jackson.JacksonModule"
                );
        initCb(routerBuilder);

        routerBuilder.method("createModule", mth -> {
            mth.withModifier(PUBLIC, STATIC, FINAL)
                    .returning("VertxGuiceModule")
                    .docComment("Creates a Guice module that sets up a Verticle with all of the "
                            + "operations for the " + shape.getId().getName() + " model."
                            + "\n@return A VertxGuiceModule configured for this service")
                    .body(bb -> {

                        bb.declare("result")
                                .initializedWithNew(nb -> nb.ofType("VertxGuiceModule"))
                                .as("VertxGuiceModule");

                        bb.blankLine().lineComment("Binds ObjectMapper configured to correctly "
                                + " handle").lineComment("java.time types as ISO 8601 strings");
                        bb.invoke("withModule")
                                .withArgumentFromInvoking("loadFromMetaInfServices")
                                .onNew(nb -> nb.ofType("JacksonModule"))
                                .on("result");

                        bb.blankLine().lineComment("Generate a single verticle with routes for")
                                .lineComment("each operation in " + shape.getId().getName());
                        String verticleBuilderName = "verticleBuilder";
                        bb.declare(verticleBuilderName)
                                .initializedByInvoking("withVerticle")
                                .on("result")
                                .as("VerticleBuilder<VertxGuiceModule>");

                        ResourceGraph graph = ResourceGraphs.graph(model, shape);

                        Set<OperationShape> ops = graph.transformedClosure(shape, sh -> sh.isOperationShape() ? sh.asOperationShape().get() : null);

                        ops.forEach(op -> {
                            System.out.println("OP " + op);
                            ClassBuilder<String> operationClass = generateOperation(op, graph);
                            bb.blankLine().lineComment("Operation " + op.getId());
                            invokeRoute(bb, verticleBuilderName, routerBuilder, operationClass, op);
                            System.out.println("GEN VX OP " + op + " -> " + operationClass.fqn());
                            addTo.accept(operationClass);
                        });
                        bb.blankLine()
                                .lineComment("Finish the builder, leaving the guice module ready ")
                                .lineComment("to have start() called on it, or to be included with ")
                                .lineComment("other modules in an Injector.");
                        bb.returningInvocationOf("bind").on(verticleBuilderName);
                    });
        });
        addTo.accept(routerBuilder);
    }

    private String operationPackage(OperationShape op) {
        return packageOf(names().packageOf(op)) + ".impl";
    }

    private ClassBuilder<String> generateOperation(OperationShape op, ResourceGraph graph) {
        String implPackage = operationPackage(op);
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage)
                .named(escape(op.getId().getName()))
                .importing(
                        "io.vertx.core.Handler",
                        "io.vertx.ext.web.RoutingContext",
                        "com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse",
                        "static com.telenav.smithy.vertx.adapter.VertxRequestAdapter.smithyRequest",
                        "javax.inject.Inject",
                        OperationNames.operationInterfaceFqn(model, op)
                )
                .implementing("Handler<RoutingContext>")
                .withModifier(PUBLIC, FINAL);

        initCb(cb);
        cb.field("spi")
                .withModifier(FINAL, PRIVATE)
                .ofType(OperationNames.operationInterfaceName(op));

        if (op.getOutput().isPresent()) {
            cb.importing("com.fasterxml.jackson.databind.ObjectMapper");
            cb.field("mapper")
                    .withModifier(FINAL, PRIVATE)
                    .ofType("ObjectMapper");
        }
        StructureShape in = inputOrNull(op);
        Input input = in == null ? null : examineInput(op, in, graph, cb);

        cb.blockComment("INPUT: " + input);

        boolean readsPayload = input != null && (input.httpPayloadType() != null)
                || (input != null && input.isEmpty());
        boolean writesPayload = op.getOutput().isPresent();
        boolean needsObjectMapper = readsPayload || writesPayload;

        String authInterfaceName
                = op.getTrait(AuthenticatedTrait.class)
                        .map(auth -> {
                            String authPackage = OperationNames.authPackage(shape, names());
                            Shape authTarget = model.expectShape(auth.getPayload());
                            String authInterface = authenticateWithInterfaceName(auth.getPayload());
                            maybeImport(cb, names().packageOf(authTarget) + "." + typeNameOf(authTarget),
                                    authPackage + "." + authInterface);
                            cb.field("authenticator")
                                    .withModifier(PRIVATE, FINAL)
                                    .ofType(authInterface);
                            return authInterface;
                        }).orElse(null);

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument(OperationNames.operationInterfaceName(op), "spi");
            if (needsObjectMapper) {
                con.addArgument("ObjectMapper", "mapper");
            }
            if (authInterfaceName != null) {
                con.addArgument(authInterfaceName, "authenticator");
            }
            con.body(bb -> {
                bb.assignField("spi").ofThis().toExpression("spi");
                if (needsObjectMapper) {
                    bb.assignField("mapper").ofThis().toExpression("mapper");
                }
                if (authInterfaceName != null) {
                    bb.assignField("authenticator").ofThis().toExpression("authenticator");
                }
            });
        });

        cb.overridePublic("handle", mth -> {
            mth.addArgument("RoutingContext", "context")
                    .body(bb -> {
                        bb.declare("smithyRequest")
                                .initializedByInvoking("smithyRequest")
                                .withArgumentFromInvoking("request")
                                .on("context")
                                .inScope()
                                .as("SmithyRequest");
                        List<String> spiArgs = new ArrayList<>();
                        spiArgs.add("smithyRequest");
                        handleAuthAndGatherInput(in, op, input, bb, cb, spiArgs);
                    });
        });
        if (readsPayload) {
            generateWithContentMethod(cb);
        }
        if (writesPayload) {
            generateWriteOutputMethod(cb);
        }
        return cb;
    }

    public <C, B extends BlockBuilderBase<C, B, ?>> void handleAuthAndGatherInput(StructureShape in, OperationShape op, Input input, B bb, ClassBuilder<String> cb, List<String> spiArgs) {
        op.getTrait(AuthenticatedTrait.class)
                .ifPresentOrElse(auth -> {
                    cb.importing(CompletableFuture.class);
                    /*
WANT SOMETHING LIKE:                    
                    
        AuthenticateWithAuthUser authenticator) {
        EnhCompletableFuture<AuthUser> future = defer();
        authenticator.authenticate(BlogServiceAuthenticatedOperations.APPROVE_COMMENT,
            request, false, AuthenticationResultConsumer.create(future, false));
                     */

                    String authPackage = authPackage(shape, names());

                    Shape authTarget = model.expectShape(auth.getPayload());
                    String authTargetName = typeNameOf(authTarget);
                    String authEnumTypeName = serviceAuthenticatedOperationsEnumName(shape);

                    maybeImport(cb,
                            authPackage + "." + authEnumTypeName
                    );

                    String authEnumConstantName = authEnumTypeName + "."
                            + TypeNames.enumConstantName(op.getId().getName());

                    bb.declare("authFuture")
                            .initializedWithNew(nb -> nb.ofType("CompletableFuture<>"))
                            .as("CompletableFuture<" + authTargetName + ">");

                    cb.importing("com.mastfrog.smithy.http.AuthenticationResultConsumer");

                    bb.invoke("authenticate")
                            .withArgument(authEnumConstantName)
                            .withArgument("smithyRequest")
                            .withArgument(auth.isOptional())
                            .withArgumentFromInvoking("create")
                            .withArgument("authFuture")
                            .withArgument(auth.isOptional())
                            .on("AuthenticationResultConsumer")
                            .on("authenticator");

                    bb.invoke("whenCompleteAsync")
                            .withLambdaArgument(lb -> {
                                lb.withArgument("authResult")
                                        .withArgument("authFailure");
                                lb.body(lbb -> {
                                    String authArgument;
                                    if (auth.isOptional()) {
                                        cb.importing(Optional.class);
                                        lbb.declare("authOptional")
                                                .initializedByInvoking("ofNullable")
                                                .withArgument("authResult")
                                                .on("Optional")
                                                .as("Optional<" + authTargetName + ">");
                                        authArgument = "authOptional";
                                    } else {
                                        authArgument = "authResult";
                                    }
                                    ClassBuilder.ElseClauseBuilder<?> els
                                            = lbb.ifNotNull("authFailure")
                                                    .invoke("fail")
                                                    .withArgument("authFailure")
                                                    .on("context")
                                                    .orElse();
                                    spiArgs.add(authArgument);
                                    gatherInput(in, op, input, els, cb, spiArgs);
                                    els.endIf();
                                });
                            })
                            .withArgumentFromInvoking("nettyEventLoopGroup")
                            .onInvocationOf("vertx")
                            .on("context")
                            .on("authFuture");
                }, () -> {
                    gatherInput(in, op, input, bb, cb, spiArgs);
                });
    }

    public <C, B extends BlockBuilderBase<C, B, ?>> void gatherInput(StructureShape in, OperationShape op, Input input, B bb, ClassBuilder<String> cb, List<String> spiArgs) {
        if (in != null) {
            if (input.httpPayloadType() != null || input.isEmpty()) {
                cb.importing(input.fqn());
                input.applyImports(cb);
                String inputType = input.isEmpty() ? input.typeName() : input.httpPayloadType();
                bb.invoke("withContent")
                        .withClassArgument(inputType)
                        .withArgument("context")
                        .withLambdaArgument(lb -> {
                            lb.withArgument("payload")
                                    .body(lbb -> {
                                        assembleOperationInput(lbb, op, input, cb, "payload", spiArgs);
                                    });
                        }).inScope();
            } else {
                assembleOperationInput(bb, op, input, cb, "payload", spiArgs);
            }
        } else {
            generateResponseHandling(bb, op, cb, spiArgs);
        }
    }

    public <C, B extends BlockBuilderBase<C, B, ?>> void generateResponseHandling(B bb, OperationShape op, ClassBuilder<?> cb, List<String> spiArgs) {
        cb.importing(CompletableFuture.class);
        cb.importing(
                "static com.telenav.smithy.vertx.adapter.VertxResponseCompletableFutureAdapter.smithyResponse"
        );
        spiArgs.add("response");
        String outputTypeName = op.getOutput().map(outId -> {
            Shape outShape = model.expectShape(outId);
            String result = typeNameOf(outShape);
            maybeImport(cb, names().packageOf(outShape) + "." + result);
            return result;
        }).orElse("Void");
        bb.declare("fut")
                .initializedWithNew(nb -> nb.ofType("CompletableFuture<>"))
                .as("CompletableFuture<" + outputTypeName + ">");
        bb.declare("response")
                .initializedByInvoking("smithyResponse")
                .withArgument("context")
                .withArgument("fut")
                .inScope()
                .as("SmithyResponse<" + outputTypeName + ">");
        bb.trying(tri -> {
            ClassBuilder.InvocationBuilder<?> inv = tri.invoke("respond");
            for (String arg : spiArgs) {
                inv = inv.withArgument(arg);
            }
            inv.on("spi");
            tri.catching(cat -> {
                cat.invoke("fail")
                        //                        .withArgument(400)
                        .withArgument("thrown")
                        .on("context");
                cat.statement("return");
//                cat.catching(cat2 -> {
//                    cat2.invoke("fail")
//                            .withArgument("thrown")
//                            .on("context");
//                    cat2.statement("return");
//                }, "Exception");
            }, "Exception");
        });
        bb.invoke("whenCompleteAsync")
                .withLambdaArgument(lb -> {
                    lb.withArgument("output")
                            .withArgument("thrown")
                            .body(lbb -> {
                                lbb.ifNotNull("thrown")
                                        .invoke("fail")
                                        .withArgument("thrown")
                                        .on("context")
                                        .orElse()
                                        .invoke("writeOutput")
                                        .withArgument("output")
                                        .withArgument("context")
                                        .inScope()
                                        .endIf();
                            });
                })
                .withArgumentFromInvoking("nettyEventLoopGroup")
                .onInvocationOf("vertx")
                .on("context")
                .on("fut");
    }

    public <C, B extends BlockBuilderBase<C, B, ?>> void assembleOperationInput(B bb, OperationShape op, Input input, ClassBuilder<String> cb, String payloadVar, List<String> spiArgs) {
        cb.importing(input.fqn());
        if (input.httpPayloadType() != null && input.size() == 1) {
            spiArgs.add(payloadVar);
            generateResponseHandling(bb, op, cb, spiArgs);
            return;
        } else if (input.isEmpty()) {
            spiArgs.add(payloadVar);
            generateResponseHandling(bb, op, cb, spiArgs);
            return;
        }

        bb.lineComment("Have input " + input);
        List<String> inputVariables = new ArrayList<>();
        for (InputMemberObtentionStrategy strat : input) {
            bb.lineComment("IMOS: " + strat);
            if (strat.type() == OriginType.HTTP_PAYLOAD) {
                inputVariables.add(payloadVar);
            } else {
                strat.comment(bb);
                inputVariables.add(strat.generateObtentionCode(cb, bb));
            }
        }
        bb.lineComment("Input vars: " + inputVariables);
        String nm = input.typeName();
        bb.declare("input").initializedWithNew(nb -> {
            for (String arg : inputVariables) {
                nb.withArgument(arg);
            }
            nb.ofType(nm);
        }).as(nm);
        spiArgs.add("input");
        generateResponseHandling(bb, op, cb, spiArgs);
    }

    private <C> void generateWriteOutputMethod(ClassBuilder<C> cb) {
        cb.importing(
                "com.fasterxml.jackson.core.JsonProcessingException",
                "io.vertx.core.Future",
                "io.vertx.core.buffer.Buffer"
        );
        cb.method("writeOutput", mth -> {
            mth.withModifier(PRIVATE)
                    .withTypeParam("T")
                    .addArgument("T", "output")
                    .addArgument("RoutingContext", "context")
                    .returning("Future<Void>");
            mth.body(bb -> {
                bb.ifNull("output")
                        .returningInvocationOf("send")
                        .onInvocationOf("response")
                        .on("context").endIf();
                bb.trying(tri -> {
                    tri.returningInvocationOf("send")
                            .withArgumentFromInvoking("buffer")
                            .withArgumentFromInvoking("writeValueAsBytes")
                            .withArgument("output")
                            .on("mapper")
                            .on("Buffer")
                            .onInvocationOf("response")
                            .on("context");
                    tri.catching(cat -> {
                        cat.invoke("fail")
                                .withArgument("thrown")
                                .on("context");
                        cat.returningInvocationOf("failedFuture")
                                .withArgument("thrown")
                                .on("Future");
                    }, "JsonProcessingException");
                });
            });
        });
        /*
    private <T> Future<Void> writeOutput(T output, RoutingContext ctx) {
        if (output == null) {
            return ctx.response().send();
        }
        try {
            return ctx.response().send(Buffer.buffer(mapper.writeValueAsBytes(output)));
        } catch (JsonProcessingException ex) {
            ctx.fail(ex);
            return Future.failedFuture(ex);
        }
    }        
         */
    }

    private <C> void generateWithContentMethod(ClassBuilder<C> cb) {
        cb.importing(
                "io.netty.buffer.ByteBufInputStream",
                validationExceptions().fqn(),
                "java.io.IOException",
                "java.io.InputStream"
        );
        cb.importing(Consumer.class);
        cb.method("withContent", mth -> {
            mth.withModifier(PRIVATE)
                    .withTypeParam("T")
                    .addArgument("Class<T>", "type")
                    .addArgument("RoutingContext", "context")
                    .addArgument("Consumer<T>", "consumer");
            mth.body(bb -> {
                bb.declare("input").as("T");
                bb.trying(tri -> {
                    tri.declare("stream")
                            .initializedWithNew(nb -> {
                                nb.withArgumentFromInvoking("getByteBuf")
                                        .onInvocationOf("buffer")
                                        .onInvocationOf("body")
                                        .on("context")
                                        .ofType("ByteBufInputStream");
                            }).as("InputStream");
                    tri.trying(innerTry -> {
                        innerTry.assign("input")
                                .toInvocation("readValue")
                                .withArgument("stream")
                                .withArgument("type")
                                .onField("mapper").ofThis();
                        innerTry.fynalli(fi -> fi.invoke("close").on("stream"));
                    });
                    tri.catching(cat -> {
                        cat.invoke("fail")
                                .withArgument(400)
                                .withArgument("thrown")
                                .on("context");
                        cat.statement("return");
                    }, "IOException", validationExceptions().name());
                });
                bb.invoke("accept")
                        .withArgument("input")
                        .on("consumer");
            });

        });
    }

    private <B> void invokeRoute(BlockBuilder<B> bb, String verticleBuilderName,
            ClassBuilder<String> routerBuilder, ClassBuilder<String> operationClass,
            OperationShape op) {
        routerBuilder.importing(operationClass.fqn());
        ClassBuilder.InvocationBuilder<BlockBuilder<B>> inv = bb.invoke("handledBy")
                .withClassArgument(operationClass.className());

        Optional<HttpTrait> httpOpt = op.getTrait(HttpTrait.class);
        if (!httpOpt.isPresent()) {
            throw new Error("No @http on " + op);
        }
        HttpTrait http = httpOpt.get();

        PathInfo info = new PathInformationExtractor(model, http.getUri())
                .withLeadingSlashInRegex()
                .extractPathInfo(inputOrNull(op));

        if (info.isRegex()) {
            inv = inv.onInvocationOf("withRegex").withStringLiteral(info.text());
        } else {
            inv = inv.onInvocationOf("withPath").withStringLiteral(info.text());
        }

        inv = inv.onInvocationOf("forHttpMethod")
                .withArgument("HttpMethod." + http.getMethod().toUpperCase());

        inv.onInvocationOf("route").on(verticleBuilderName);
    }

    private StructureShape inputOrNull(OperationShape op) {
        return op.getInput().flatMap(id -> {
            Shape sh = model.expectShape(id);
            return sh.asStructureShape();
        }).orElse(null);
    }

    private Input examineInput(OperationShape op, StructureShape input, ResourceGraph graph,
            ClassBuilder<String> cb) {
        Optional<HttpTrait> httpOpt = op.getTrait(HttpTrait.class);
        List<InputMemberObtentionStrategy> st = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append(" ************** STRATEGIES **************");
        sb.append("\n Input ").append(typeNameOf(input)).append(" - ").append(input.getId());
        sb.append("\n has @http? ").append(httpOpt);
        if (httpOpt.isPresent()) {
            HttpTrait http = httpOpt.get();
            UriPattern pattern = http.getUri();

//            addNumericQueryParametersAnnotation(input, cb);
            for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {

                MemberShape m = e.getValue();
                Shape memberTarget = model.expectShape(m.getTarget());

                sb.append("\n  * ").append(e.getKey()).append(" ").append(typeNameOf(memberTarget))
                        .append(" - ").append(memberTarget.getType());

                // Ensure the member type is imported
                names().typeNameOf(cb, memberTarget, false);
                if (m.getTrait(HttpPayloadTrait.class).isPresent()) {
                    sb.append(" - payload");
                    StructureShape payloadShape = model.expectShape(m.getTarget(), StructureShape.class);
                    String fqn = names().packageOf(payloadShape) + "."
                            + typeNameOf(payloadShape);
                    maybeImport(cb, fqn);
//                    cb.importing("com.mastfrog.acteur.preconditions.InjectRequestBodyAs");
//                    cb.annotatedWith("InjectRequestBodyAs", anno -> {
//                        anno.addClassArgument("value", typeNameOf(payloadShape));
//                    });
                    st.add(new InputMemberObtentionStrategy(new PayloadOrigin(fqn), payloadShape, m, names()));
                } else if (m.getTrait(HttpLabelTrait.class).isPresent()) {
                    HttpLabelTrait t = m.expectTrait(HttpLabelTrait.class);
                    String name = m.getMemberName();
                    // XXX check the trait that can provide an alternate name

                    List<SmithyPattern.Segment> segs = pattern.getSegments();
                    int ix = -1;
                    for (int i = 0; i < segs.size(); i++) {
                        SmithyPattern.Segment seg = segs.get(i);
                        if (seg.isLabel()) {
                            if (seg.getContent().equals(name)) {
                                ix = i;
                                break;
                            }
                        }
                    }
                    sb.append(" - label ").append(ix).append(" - ").append(t.toShapeId());

                    RequestParameterOrigin uq = new RequestParameterOrigin(Integer.toString(ix), URI_PATH, declarationFor(URI_PATH, memberTarget, m, model, cb));

                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpQueryTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    RequestParameterOrigin uq = new RequestParameterOrigin(name, URI_QUERY,
                            declarationFor(URI_QUERY, memberTarget, m, model, cb));
                    
                    sb.append(" - query ").append(name);
                    // XXX check the trait that can provide an alternate name
                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                } else if (m.getTrait(HttpHeaderTrait.class).isPresent()) {
                    String name = m.getMemberName();
                    HttpHeaderTrait trait = m.getTrait(HttpHeaderTrait.class).get();
                    sb.append(" - header " + trait.getValue());

                    RequestParameterOrigin uq = new RequestParameterOrigin(trait.getValue(), HTTP_HEADER,
                            declarationFor(HTTP_HEADER, memberTarget, m, model, cb));
                    // XXX check the trait that can provide an alternate name
                    st.add(new InputMemberObtentionStrategy(uq,
                            model.expectShape(m.getTarget()), m, names()));
                }
            }
        } else {
            sb.append("\n NO @http present");
//            throw new UnsupportedOperationException("HTTP input inferencing not implemented yet.");
        }
        cb.blockComment(sb.toString());
//        if (st.isEmpty()) {
//            String fqn = names().packageOf(shape) + "." + TypeNames.typeNameOf(shape);
//            InputMemberObtentionStrategy strat = new InputMemberObtentionStrategy(new PayloadOrigin(fqn), shape, null);
//            st.add(strat);
//        }
        return new Input(input, st, op, names());
    }

    <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr>
            Declaration<?, ?, ?, ?>
            declarationFor(OriginType type, Shape memberTarget, MemberShape member, Model model,
                    ClassBuilder<?> cb) {

        boolean required = member.getTrait(RequiredTrait.class).isPresent();
        Optional<DefaultTrait> def = member.getTrait(DefaultTrait.class)
                .or(() -> memberTarget.getTrait(DefaultTrait.class));
        boolean isModelType = !"smithy.api".equals(memberTarget.getId().getNamespace());

        Declarer<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>> decl;
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

    static <B extends ClassBuilder.BlockBuilderBase<Tr, B, Rr>, Tr, Rr, Ir extends ClassBuilder.InvocationBuilderBase<ClassBuilder.TypeAssignment<B>, Ir>>
            Declaration<B, Tr, Rr, ?>
            applyDeclaration(Declarer<B, Tr, Rr, Ir> dec, OriginType type, boolean required, boolean isModelType,
                    Shape memberTarget, MemberShape member, Model model, ClassBuilder<?> cb) {

        TypeNames tn = new TypeNames(model);
        Declarer<B, Tr, Rr, ClassBuilder.InvocationBuilder<ClassBuilder.TypeAssignment<B>>> res;
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

    interface AuthInfoConsumer {

        void authInfo(Shape payload, String mechanism, String pkg, String payloadType, boolean optional);
    }

    private void withAuthInfo(OperationShape shape, AuthInfoConsumer c) {
        withAuthInfo(shape, model, names(), c);
    }

    public static void withAuthInfo(OperationShape shape, Model model, TypeNames names, AuthInfoConsumer c) {
        shape.getTrait(AuthenticatedTrait.class).ifPresent(auth -> {
            Shape payload = model.expectShape(auth.getPayload());
            String pkg = names.packageOf(payload);
            String nm = typeNameOf(payload);
            c.authInfo(payload, auth.getMechanism(), pkg, nm, auth.isOptional());
        });
    }

}
