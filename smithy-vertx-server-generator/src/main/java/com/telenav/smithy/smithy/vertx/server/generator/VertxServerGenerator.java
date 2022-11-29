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
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationLogger;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.smithy.java.generators.util.JavaSymbolProvider.escape;
import com.mastfrog.smithy.java.generators.util.TypeNames;
import static com.mastfrog.smithy.java.generators.util.TypeNames.packageOf;
import static com.mastfrog.smithy.java.generators.util.TypeNames.typeNameOf;
import com.telenav.smithy.utils.ResourceGraph;
import com.telenav.smithy.utils.ResourceGraphs;
import com.telenav.smithy.utils.path.PathInfo;
import com.telenav.smithy.utils.path.PathInformationExtractor;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 *
 * @author Tim Boudreau
 */
public class VertxServerGenerator extends AbstractJavaGenerator<ServiceShape> {

    public VertxServerGenerator(ServiceShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings, SmithyGenerationLogger logger) {
        super(shape, model, destSourceRoot, target, language);
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
                            ClassBuilder<String> operationClass = generateOperation(op);
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

    private ClassBuilder<String> generateOperation(OperationShape op) {
        String implPackage = operationPackage(op);
        ClassBuilder<String> cb = ClassBuilder.forPackage(implPackage)
                .named(escape(op.getId().getName()))
                .importing(
                        "io.vertx.core.Handler",
                        "io.vertx.ext.web.RoutingContext",
                        "com.mastfrog.smithy.http.SmithyRequest",
                        "com.mastfrog.smithy.http.SmithyResponse",
                        "static com.telenav.smithy.vertx.adapter.VertxRequestAdapter.smithyRequest",
                        "static com.telenav.smithy.vertx.adapter.VertxResponseAdapter.smithyResponse",
                        "javax.inject.Inject",
                        interfaceFqn(model, op)
                )
                .implementing("Handler<RoutingContext>")
                .withModifier(PUBLIC, FINAL);

        cb.field("spi")
                .withModifier(FINAL, PRIVATE)
                .ofType(interfaceName(op));

        if (op.getOutput().isPresent()) {
            cb.importing("com.fasterxml.jackson.databind.ObjectMapper");
            cb.field("mapper")
                    .withModifier(FINAL, PRIVATE)
                    .ofType("ObjectMapper");
        }

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument(interfaceName(op), "spi");
            if (op.getOutput().isPresent()) {
                con.addArgument("ObjectMapper", "mapper");
            }
            con.body(bb -> {
                bb.assignField("spi").ofThis().toExpression("spi");
                if (op.getOutput().isPresent()) {
                    bb.assignField("mapper").ofThis().toExpression("mapper");
                }
            });
        });

        // public void handle(RoutingContext context) 
        cb.overridePublic("handle", mth -> {
            mth.addArgument("RoutingContext", "context")
                    .body(bb -> {
                        bb.declare("request")
                                .initializedByInvoking("smithyRequest")
                                .withArgumentFromInvoking("request")
                                .on("context")
                                .inScope()
                                .as("SmithyRequest");

                        op.getInput().ifPresentOrElse((ShapeId in) -> {
                            Shape inputShape = model.expectShape(in);
                            String tn = typeNameOf(inputShape);
                            maybeImport(cb, names().packageOf(inputShape) + "." + tn);
                            bb.declare("input")
                                    .initializedWith("null")
                                    .as(tn);

                        }, () -> {

                        });

                    });
        });
        return cb;
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

    // Below are copy/pasted from OperationInterfaceGenerator - should be unified
    static String interfaceName(OperationShape shape) {
        return TypeNames.typeNameOf(shape) + "Responder";
    }

    static String interfaceFqn(Model mdl, OperationShape shape) {
        TypeNames tn = new TypeNames(mdl);
        String pkg = tn.packageOf(shape);
        String ifaceName = interfaceName(shape);
        return pkg + "." + ifaceName;
    }

}
