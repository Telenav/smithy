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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.function.state.Bool;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SettingsKey;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.smithy.ts.generator.SimpleStructureGenerator.httpHeaderItems;
import static com.telenav.smithy.smithy.ts.generator.SimpleStructureGenerator.httpQueryItems;
import static com.telenav.smithy.smithy.ts.generator.SimpleStructureGenerator.withHttpPayloadItem;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InvocationBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import static com.telenav.smithy.ts.vogon.TypescriptSource.typescript;
import com.telenav.smithy.utils.ResourceGraphs;
import java.nio.file.Path;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ExpectationNotMetException;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 *
 * @author Tim Boudreau
 */
class BrowserSDKGenerator extends AbstractTypescriptGenerator<ServiceShape> {

    private final SettingsKey<Set<Model>> WKEY
            = SettingsKey.key(Set.class, "serviceClientWritten");

    BrowserSDKGenerator(ServiceShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    private boolean shouldAddServiceClient() {
        Set<Model> modelsGeneratedFor = ctx.computeIfAbsent(WKEY, () -> {
            return newSetFromMap(synchronizedMap(new WeakHashMap<>()));
        });
        return modelsGeneratedFor.add(model);
    }

    static String serviceClientName(ServiceShape shape) {
        return escape(capitalize(shape.getId().getName() + "Client"));
    }

    String serviceClientName() {
        return serviceClientName(shape);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = typescript(shape.getId().getName() + "Client");
        src.importing("ServiceClient").and("serviceClient")
                .from("./ServiceClient");

//        src.generateDebugLogCode();
        String configInterface = escape(shape.getId().getName() + "Config");

        generateConfigInterface(src, configInterface);
        generateConfigFromUriFunction(src, configInterface);

        InterfaceBuilder<TypescriptSource> iface = src.declareInterface(
                serviceClientName())
                .exported()
                .docComment("SDK client interface for the " + shape.getId().getName() + "."
                        + " Use the provided factory method `" + decapitalize(shape.getId().getName()) + "Client to "
                        + "obtain an instance for use.\n"
                        + "If you wish to monitor all network activity from this client (in "
                        + "order to indicate network activity or log it), call `listen()` on the object "
                        + "returned by the `serviceClient` property with a listener function.");
        TypescriptSource.ClassBuilder<TypescriptSource> cb = src.declareClass(iface.name() + "Impl")
                .docComment("Internal implementation of the `" + iface.name() + "`.");

        generateInterfaceAndClient(configInterface, src, iface, cb);

        iface.close();
        cb.close();

        src.function(decapitalize(iface.name()))
                .docComment("Create a new " + iface.name() + ".")
                .exported()
                .withArgument("config")
                .optional().or().withType("string")
                .withType("null")
                .ofType(configInterface)
                .returning(iface.name())
                .returningNew()
                .withArgument("null")
                .withArgument("config")
                .ofType(cb.name());

        generateAssembleUriFunction(configInterface, src);

        c.accept(src);
    }

    public void generateConfigInterface(TypescriptSource src, String configInterface) {
        src.declareInterface(configInterface, iface -> {
            iface.docComment("Configuration object for the " + shape.getId().getName()
                    + " client; only needed if you need to customize the host, port, protocol "
                    + "or URI path prefix used by requests to the " + shape.getId().getName()
                    + " service.");
            iface.exported().property("hostAndPort")
                    .optional()
                    .ofType("string");

            iface.property("protocol")
                    .optional()
                    .ofType("string");

            iface.property("pathPrefix")
                    .optional()
                    .ofType("string");
        });
    }

    public void generateConfigFromUriFunction(TypescriptSource src, String configInterface) {
        src.function("configFromUri", f -> {
            f.exported()
                    .withArgument("url").ofType("string")
                    .returning(configInterface);
            f.body(bb -> {
                bb.declare("result")
                        .ofType(configInterface)
                        .assignedToObjectLiteral().endObjectLiteral();
                bb.declare("u")
                        .assignedToNew()
                        .withArgument("url")
                        .ofType("URL");

                bb.iff("u.protocol")
                        .iff().operation(TypescriptSource.BinaryOperations.EQUALLING)
                        .invoke("charAt").withArgument().operation(TypescriptSource.BinaryOperations.MINUS)
                        .field("length").ofField("protocol").of("u")
                        .literal(1)
                        .onField("protocol").of("u")
                        .literal(":")
                        .assignField("protocol")
                        .of("result")
                        .toInvocationOf("substring")
                        .withArgument(0)
                        .withArgument().operation(TypescriptSource.BinaryOperations.MINUS)
                        .field("length").ofField("protocol").of("u")
                        .literal(1)
                        .onField("protocol")
                        .of("u")
                        .orElse()
                        .assignField("protocol").of("result")
                        .toField("protocol").of("u")
                        .endIf().endIf();
                bb.iff("u.hostname")
                        .assignField("hostAndPort").of("result").toField("hostname").of("u")
                        .endIf();

                bb.iff("u.port")
                        .assignField("hostAndPort").of("result")
                        .toStringConcatenation(cat -> {
                            cat.appending()
                                    .parenthesized()
                                    .operation(TypescriptSource.BinaryOperations.LOGICAL_OR)
                                    .field("hostAndPort").of("result")
                                    .literal("localhost")
                                    .append(":")
                                    .appendField("port")
                                    .of("u")
                                    .endConcatenation();
                        }).endIf();

//                bb.iff("u.port")
//                        .statement("result.hostAndPort = (result.hostAndPort || 'localhost') + ':' + u.port")
//                        .endIf();
                bb.iff().operation(TypescriptSource.BinaryOperations.LOGICAL_AND)
                        .field("pathname").of("u")
                        .operation(TypescriptSource.BinaryOperations.NOT_EQUALLING)
                        .field("pathname").of("u")
                        .literal("/")
                        .declareConst("strippingLeadingAndTrailingSlashes")
                        .assignedToInvocationOf("exec")
                        .withArgument("u.pathname")
                        .onNew()
                        .withStringLiteralArgument("/?(.*)/?")
                        .ofType("RegExp")
                        .iff("strippingLeadingAndTrailingSlashes")
                        .assignField("pathPrefix").of("result")
                        .toElement().literal(1).of("strippingLeadingAndTrailingSlashes")
                        //                        .statement("result.pathPrefix = strippingLeadingAndTrailingSlashes[1]")
                        .endIf()
                        .endIf();
                bb.returning("result");
            });
        });
    }

    private void generateAssembleUriFunction(String configInterface, TypescriptSource cb) {
        cb.function("assembleUri", f -> {
            f.docComment("Internal function to apply the configuration to the final uri"
                    + " used to make requests to the " + shape.getId().getName() + " service.");
            f.withArgument("path").ofType("string")
                    .withArgument("config")
                    .ofType(configInterface);
            f.body(bb -> {
                bb.iff("config.hostAndPort", iff -> {
                    iff.declare("protocol").assignedTo()
                            .operation(TypescriptSource.BinaryOperations.LOGICAL_OR)
                            .field("protocol").of("config")
                            .literal("http");
//                    iff.statement("let protocol = config.protocol || 'http'");
                    iff.returning("protocol + '://' + config.hostAndPort + '/' + path");
                });
                bb.returning("'/' + path");
            });
        });
    }

    @Override
    protected void generateAdditional(Consumer<GeneratedCode> c) {
        if (shouldAddServiceClient()) {
            c.accept(resource("ServiceClient.ts", "client_proto.ts"));
            if (BrowserUIDomGenerator.shouldGenerateUI(ctx.settings())) {
                c.accept(resource("index.html", "domstuff.html"));
                c.accept(resource("domstuff.ts", "domstuff.ts"));
            }
        }
    }

    private void generateInterfaceAndClient(String configInterface,
            TypescriptSource src, InterfaceBuilder<TypescriptSource> iface,
            ClassBuilder<TypescriptSource> cb) {

        cb.property("client").setPrivate().readonly()
                .ofType("ServiceClient");
        cb.property("config").setPrivate().readonly()
                .ofType(configInterface);

        cb.constructor(con -> {
            con.withArgument("client").optional().or().withType("null").ofType("ServiceClient")
                    .withArgument("config").optional().or().withType("string").withType("null").ofType(configInterface);
            con.body(bb -> {
                bb.statement("this.client = client || serviceClient()");
                bb.statement("this.config = typeof config ==='string' ? configFromUri(config) : config || {}");
            });
        });

        iface.property("serviceClient").readonly().ofType("ServiceClient");
        cb.getter("serviceClient", bb -> bb.returningField("client").ofThis());

        Set<OperationShape> ops
                = ResourceGraphs.graph(model, shape)
                        .transformedClosure(shape, sh -> sh.asOperationShape().orElse(null));

        for (OperationShape op : ops) {
            generateOneOperation(op, src, iface, cb);
        }
    }

    private void importModelShape(TypescriptSource src, Shape type) {
        if (!"smithy.api".equals(type.getId().getNamespace())) {
            src.importing(tsTypeName(type)).from("./" + src().name());
        }
    }

    private void generateOneOperation(OperationShape op, TypescriptSource src,
            InterfaceBuilder<TypescriptSource> iface, ClassBuilder<TypescriptSource> cb) {
        String methodName = operationMethodName(op);

        op.getTrait(HttpTrait.class).orElseThrow(()
                -> new ExpectationNotMetException(
                        "Operation does not have the @http trait; inferred http not implemented.", op));

        Optional<Shape> inputShape = op.getInput().map(inp -> model.expectShape(inp));
        Optional<Shape> outputShape = op.getOutput().map(outp -> model.expectShape(outp));

        Optional<String> inputType = inputShape.map(shp -> {
            importModelShape(src, shp);
            return tsTypeName(shp);
        });

        Optional<String> outputType = outputShape.map(out -> {
            importModelShape(src, out);
            return tsTypeName(out);
        });

        cb.method(methodName, mth -> {
            iface.method(methodName, imeth -> {
                op.getTrait(DocumentationTrait.class)
                        .ifPresent(dox -> {
                            imeth.docComment(dox.getValue());
                        });

                inputType.ifPresent(inp -> {
                    imeth.withArgument("input").ofType(inp);
                    mth.withArgument("input").ofType(inp);
                });
                outputType.ifPresentOrElse(out -> {
                    imeth.returning("Promise<" + out + ">");
                    mth.returning("Promise<" + out + ">");
                }, () -> {
                    imeth.returning("Promise<boolean>");
                    mth.returning("Promise<boolean>");
                });
            });
            mth.makePublic();
            op.getTrait(DocumentationTrait.class)
                    .ifPresent(dox -> {
                        mth.docComment(dox.getValue());
                    });
            mth.body(bb -> {
                generateServiceCall(op, src, cb, inputType, inputShape, outputType,
                        outputShape, bb);
            });
        });
    }

    static String operationMethodName(OperationShape op) {
        return escape(decapitalize(op.getId().getName()));
    }

    private void generateServiceCall(OperationShape op, TypescriptSource src,
            ClassBuilder<TypescriptSource> cb, Optional<String> inputType,
            Optional<Shape> inputShape,
            Optional<String> outputType, Optional<Shape> outputShape,
            TsBlockBuilder<Void> bb) {

        HttpTrait http = op.expectTrait(HttpTrait.class);

        bb.declare("uriElements")
                .ofType("any[]")
                .assignedTo("[]");
        bb.iff("this.config.pathPrefix")
                .invoke("push")
                .withField("pathPrefix")
                .ofField("config").ofThis()
                .on("uriElements")
                .endIf();

        http.getUri().getSegments().forEach(seg -> {
            if (!seg.isLabel()) {
                bb.invoke("push")
                        .withStringLiteralArgument(seg.getContent())
                        .on("uriElements");
            } else {
                MemberShape mem = inputShape.get().getAllMembers().get(seg.getContent());
                if (mem == null) {
                    throw new ExpectationNotMetException("No member named " + seg.getContent()
                            + " but a uri path element with that name is specified in its http trait", op);
                }
                String fieldName = escape(seg.getContent());
                Shape inp = inputShape.get();
                switch (inp.getType()) {
                    case LIST:
                    case SET:
                        bb.invoke("push")
                                .withInvocationOf("toString")
                                .onField(fieldName)
                                .of("input")
                                .on("uriElements");
                        break;
                    default:
                        bb.invoke("push")
                                .withField(fieldName)
                                .of("input")
                                .on("uriElements");
                        break;
                }
            }
        });

        Bool hasQueryParams = Bool.create();
        Bool hasHeaderParams = Bool.create();
        inputShape.ifPresent(inp -> {
            Map<String, Map.Entry<String, MemberShape>> headerItems = httpHeaderItems(model, inp.asStructureShape().get());
            if (!headerItems.isEmpty()) {
                hasHeaderParams.set();
                bb.declare("headers")
                        .ofType("object").assignedTo("{}");
                bb.invoke("populateHttpHeaders").withArgument("headers").on("input");
            }
            Map<String, Map.Entry<String, MemberShape>> queryItems = httpQueryItems(model, inp.asStructureShape().get());
            if (!queryItems.isEmpty()) {
                hasQueryParams.set();
                bb.declare("query")
                        .ofType("object").assignedTo("{}");
                bb.invoke("populateHttpQuery").withArgument("query").on("input");
            }
        });

        String mth = http.getMethod().toLowerCase();
        InvocationBuilder<Void> partialInvocation = bb.returningInvocationOf("result")
                .withLambda().withArgument("obj")
                .ofType("object")
                .body(lbb -> {
                    outputType.ifPresentOrElse(out -> {
                        if ("smithy.api".equals(outputShape.get().getId().getNamespace())) {
                            lbb.returning("obj as " + out);
                        } else {
                            lbb.returningInvocationOf("fromJsonObject")
                                    .withArgument("obj")
                                    .on(out);
                        }
                    }, () -> {
                        lbb.returning("true");
                    });
                }).onInvocationOf(mth)
                .withObjectLiteral(lit -> {
                    lit.assigning("uri")
                            .toInvocationOf("assembleUri")
                            .withInvocationOf("join")
                            .withStringLiteralArgument("/")
                            .on("uriElements")
                            .withField("config").ofThis()
                            .inScope();

                    hasQueryParams.ifTrue(() -> {
                        lit.assigning("queryParams").toExpression("query");
                    });
                    hasHeaderParams.ifTrue(() -> {
                        lit.assigning("headers").toExpression("headers");
                    });
                });
        InvocationBuilder<Void> inv = inputShape.flatMap(inp -> inp.asStructureShape()).flatMap(struct -> {
            return withHttpPayloadItem(model, struct, (name, member, payloadShape)
                    -> {
                if (member == null) {
                    return partialInvocation.withArgument("input");
                } else {
                    return partialInvocation.withField(name).of("input");
                }
            }
            );
        }).orElse(partialInvocation);
        inv.onField("serviceClient").ofThis();
    }
}
