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

import com.telenav.smithy.utils.ResourceGraph;
import com.mastfrog.function.TriConsumer;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.TypeAssignment;
import static com.mastfrog.smithy.generators.GenerationSwitches.DEBUG;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import com.mastfrog.smithy.java.generators.builtin.struct.impl.Registry;
import static com.telenav.smithy.names.JavaSymbolProvider.escape;
import com.telenav.smithy.names.TypeNames;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import com.mastfrog.util.preconditions.ConfigurationError;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.pattern.SmithyPattern.Segment;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
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
public class ServiceClientGenerator extends AbstractJavaGenerator<ServiceShape> {

    private final ResourceGraph graph;
    private final boolean generateDebugComments;

    public ServiceClientGenerator(ServiceShape shape, Model model, Path destSourceRoot,
            GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings sgs) {
        super(shape, model, destSourceRoot, target, language);
        generateDebugComments = sgs.is(DEBUG);
        graph = OperationGenerator.ensureGraphs(model, shape);
    }

    static String clientPackage(ServiceShape service, Model model) {
        return new TypeNames(model).packageOf(service) + ".client";
    }

    String clientPackage() {
        return clientPackage(shape, model);
    }

    String clientClassName() {
        return typeNameOf(shape) + "Client";
    }

    private Set<OperationShape> allOperations() {
        Set<OperationShape> result = graph.transformedClosure(shape, sh -> {
            if (sh.isOperationShape()) {
                return sh.asOperationShape().get();
            }
            return null;
        });
        // An unsorted set means the position of generated methods
        // jumps around *a lot* between builds
        Set<OperationShape> sorted = new TreeSet<>((a, b) -> {
            return a.getId().compareTo(b.getId());
        });
        sorted.addAll(result);
        return sorted;
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(clientPackage())
                .named(clientClassName())
                .withModifier(PUBLIC, FINAL)
                .importing(
                        "com.mastfrog.smithy.client.base.BaseServiceClient",
                        "com.mastfrog.smithy.client.result.ServiceResult",
                        "java.util.concurrent.CompletableFuture"
                );
        if (generateDebugComments) {
            cb.generateDebugLogCode();
        }

        Registry.applyGeneratedAnnotation(getClass(), cb);

        cb.extending("BaseServiceClient<" + cb.className() + ">");
        cb.constructor(con -> {
            con.setModifier(PUBLIC);
            con.body(bb -> {
                bb.invoke("super")
                        .withStringLiteral(shape.getId().getName())
                        .withStringLiteral(shape.getVersion())
                        .withStringLiteral("http://localhost:8123")
                        .inScope();
            });
        });
        cb.constructor(con -> {
            con.setModifier(PUBLIC)
                    .addArgument("String", "endpoint")
                    .body(bb -> {
                        bb.invoke("super")
                                .withStringLiteral(shape.getId().getName())
                                .withStringLiteral(shape.getVersion())
                                .withArgument("endpoint")
                                .inScope();
                    });
        });

        cb.overridePublic("withEndpoint", mth -> {
            mth.addArgument("String", "endpoint")
                    .returning(cb.className())
                    .body(bb
                            -> bb.returningNew(nb -> nb.withArgument("endpoint")
                    .ofType(cb.className())));
        });

        for (OperationShape op : allOperations()) {
            generateOneOperation(op, cb);
        }
        addTo.accept(cb);
    }

    private void generateOneOperation(OperationShape op, ClassBuilder<String> cb) {
        Optional<Shape> input = op.getInput().map(inId -> model.expectShape(inId));
        Optional<Shape> output = op.getOutput().map(outId -> model.expectShape(outId));
        Optional<HttpTrait> httpOpt = op.getTrait(HttpTrait.class);
        if (!input.isPresent() && !output.isPresent()) {
            throw new ConfigurationError(op.getId()
                    + " has no input AND no output");
        }
        if (!httpOpt.isPresent()) {
            throw new ConfigurationError(op.getId()
                    + " has no http trait - will not infer url mapping (yet)");
        }
        input.ifPresent(in -> {
            cb.importing(names().packageOf(in) + "." + typeNameOf(in));
        });
        output.ifPresent(out -> {
            cb.importing(names().packageOf(out) + "." + typeNameOf(out));
        });
        String methodName = decapitalize(escape(op.getId().getName()));
        cb.method(methodName, mth -> {
            op.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                mth.docComment(dox.getValue()
                        + "\n@return a future containing a service result"
                );
            });
            mth.withModifier(PUBLIC);
            input.ifPresent(in -> {
                mth.addArgument(typeNameOf(in), "input");
            });
            output.ifPresentOrElse(out -> {
                mth.returning("CompletableFuture<ServiceResult<"
                        + typeNameOf(out) + ">>");
            }, () -> {
                mth.returning("CompletableFuture<ServiceResult<Void>>");
            });
            mth.body(bb -> {
                generateInvocationForOp(op, httpOpt.get(), input, output, cb, bb);
            });
        });
    }

    private <T, B extends BlockBuilderBase<T, BlockBuilder<T>, T>>
            void generateInvocationForOp(OperationShape op, HttpTrait http, Optional<Shape> input,
                    Optional<Shape> output, ClassBuilder<String> cb, B bb) {
        UriPattern uriPattern = http.getUri();

        InvocationBuilder<TypeAssignment<BlockBuilder<T>>> inv = bb.declare("uri")
                .initializedByInvoking("build")
                .withArgumentFromInvoking("endpoint").inScope();

        bb.lineComment("URI PATTERN: " + uriPattern.getSegments());

        List<Segment> segs = uriPattern.getSegments();
        for (int i = segs.size() - 1; i >= 0; i--) {
            Segment seg = segs.get(i);
            if (seg.isLabel()) {
                if (!input.isPresent()) {
                    throw new ConfigurationError("No input present, but have a templated url path element");
                }
                inv = findInInput(seg.getContent(), false, input.get(), cb, inv);
            } else {
                inv = inv.onInvocationOf("add").withStringLiteral(seg.getContent());
            }
        }
        bb.lineComment("Have query literals: " + uriPattern.getQueryLiterals());
        for (Map.Entry<String, String> qe : uriPattern.getQueryLiterals().entrySet()) {
            if (!input.isPresent()) {
                throw new ConfigurationError("No input present, but have a templated url path element");
            }
            String lookFor = qe.getValue();
            if (lookFor.isBlank()) {
                lookFor = qe.getKey();
            }
            inv = findInInput(lookFor, true, input.get(), cb, inv);
        }

        cb.importing(URI.class);

        inv = inv.onInvocationOf("uri");
        inv.inScope()
                .as("URI");

        String httpMethod = http.getMethod().toLowerCase();
        Optional<Map.Entry<String, MemberShape>> payloadOpt = findHttpPayload(input);

        String outputType = output.map(shp -> {
            return typeNameOf(shp);
        }).orElse("Void");

        switch (httpMethod.toUpperCase()) {
            case "GET":
            case "DELETE":
                // should have no body
                if (payloadOpt.isPresent()) {
                    throw new ConfigurationError(httpMethod + " calls should not have a payload");
                }
                boolean handled = withHeaderInputProperties(input, (headerMembers, headerForMemberName, requiredHeaderTraits) -> {
                    generateHeaderSettingHttpInvocation(bb, false, httpMethod, outputType, headerMembers, headerForMemberName, requiredHeaderTraits, cb);
                });
                if (!handled) {
                    bb.returningInvocationOf(httpMethod)
                            .withArgumentFromInvoking("toString").on("uri")
                            .withClassArgument(outputType)
                            .on("super");
                }
                break;
            default:
                payloadOpt.ifPresentOrElse(payload -> {
                    String getter = escape(decapitalize(payload.getKey()));

                    boolean hadHeaderProperties1 = withHeaderInputProperties(input, (headerMembers, headerForMemberName, requiredHeaderTraits) -> {
                        generateHeaderSettingHttpInvocation(bb, true, httpMethod, outputType, headerMembers, headerForMemberName, requiredHeaderTraits, cb,
                                iv -> {
                                    return iv.withArgumentFromInvoking(getter).on("input");
                                });
                    });
                    if (!hadHeaderProperties1) {
                        bb.returningInvocationOf(httpMethod)
                                .withArgumentFromInvoking(getter).on("input")
                                .withArgumentFromInvoking("toString").on("uri")
                                .withClassArgument(outputType)
                                .on("super");
                    }
                }, () -> {
                    boolean hadHeaderProperties2 = withHeaderInputProperties(input,
                            (headerMembers, headerForMemberName, requiredHeaderTraits) -> {
                                generateHeaderSettingHttpInvocation(bb, true, httpMethod, outputType, headerMembers, headerForMemberName, requiredHeaderTraits, cb,
                                        iv -> {
                                            if (isNoInputTraits(input)) {
                                                return iv.withArgument("input");
                                            }
                                            return iv.withArgument("null");
                                        });
                            });
                    if (!hadHeaderProperties2) {
                        bb.returningInvocationOf(httpMethod)
                                // The input may simply *be* the payload, with no
                                // header/query/label traits involved
                                .withArgument(input.isPresent() ? "input" : "null")
                                .withArgumentFromInvoking("toString").on("uri")
                                .withClassArgument(outputType)
                                .on("super");
                    }
                });
        }
    }

    private boolean isNoInputTraits(Optional<Shape> inputShape) {
        if (!inputShape.isPresent()) {
            return false;
        }
        StructureShape ss = inputShape.get().asStructureShape().get();
        for (Map.Entry<String, MemberShape> e : ss.getAllMembers().entrySet()) {
            boolean found = e.getValue().getTrait(HttpQueryTrait.class).isPresent()
                    || e.getValue().getTrait(HttpLabelTrait.class).isPresent()
                    || e.getValue().getTrait(HttpHeaderTrait.class).isPresent()
                    || e.getValue().getTrait(HttpPayloadTrait.class).isPresent();
            if (found) {
                return false;
            }
        }
        return true;
    }

    public <T, B extends BlockBuilderBase<T, BlockBuilder<T>, T>> void generateHeaderSettingHttpInvocation(B bb, boolean twoArg,
            String httpMethod, String outputType, Set<Map.Entry<String, MemberShape>> headerMembers,
            Map<String, String> headerForMemberName, Set<String> requiredHeaderTraits, ClassBuilder<String> cb) {

        this.generateHeaderSettingHttpInvocation(bb, twoArg, httpMethod, outputType, headerMembers, headerForMemberName, requiredHeaderTraits, cb, x -> x);
    }

    public <T, B extends BlockBuilderBase<T, BlockBuilder<T>, T>> void generateHeaderSettingHttpInvocation(
            B bb, boolean twoArg,
            String httpMethod, String outputType, Set<Map.Entry<String, MemberShape>> headerMembers,
            Map<String, String> headerForMemberName, Set<String> requiredHeaderTraits,
            ClassBuilder<String> cb,
            UnaryOperator<InvocationBuilder<BlockBuilder<T>>> op) {

        bb.lineComment("Have header properties: " + headerForMemberName);
        InvocationBuilder<BlockBuilder<T>> ret = op.apply(bb.returningInvocationOf(httpMethod));
        ret.withArgumentFromInvoking("toString").on("uri")
                .withClassArgument(outputType)
                .withLambdaArgument(lbb -> {
                    lbb.withArgument("request");
                    if (twoArg) {
                        lbb.withArgument("_bytes");
                    }
                    lbb.body(subBb -> {
                        for (Map.Entry<String, MemberShape> e : headerMembers) {
                            String headerName = headerForMemberName.get(e.getKey());
                            Shape target = model.expectShape(e.getValue().getTarget());
                            boolean required = requiredHeaderTraits.contains(e.getKey());
                            String methodName = escape(decapitalize(e.getKey()));
                            boolean isRawType = "smithy.api".equals(target.getId().getNamespace());
                            Function<InvocationBuilder<?>, InvocationBuilder<?>> f = iv -> {
                                switch (target.getType()) {
                                    case TIMESTAMP:
                                        cb.importing("com.mastfrog.smithy.http.HeaderTypes");
                                        InvocationBuilder<InvocationBuilder<?>> subIv = (InvocationBuilder) iv.withArgumentFromInvoking("toString")
                                                .onInvocationOf("toCharSequence");
                                        if (!isRawType) {
                                            subIv = subIv.onInvocationOf("get");
                                        }
                                        if (!required) {
                                            iv = subIv
                                                    .withArgumentFromInvoking("get")
                                                    .onInvocationOf(methodName)
                                                    .on("input")
                                                    .onInvocationOf("dateHeader")
                                                    .withStringLiteral(headerName)
                                                    .onInvocationOf("headerTypes")
                                                    .on("HeaderTypes");
                                        } else {
                                            iv = subIv
                                                    .withArgumentFromInvoking(methodName)
                                                    .on("input")
                                                    .onInvocationOf("dateHeader")
                                                    .withStringLiteral(headerName)
                                                    .onInvocationOf("headerTypes")
                                                    .on("HeaderTypes");
                                        }
                                        break;
                                    default:
                                        InvocationBuilder<InvocationBuilder<?>> div = (InvocationBuilder) iv.withArgumentFromInvoking("toString");
                                        if (!required) {
                                            iv = div.onInvocationOf("get")
                                                    .onInvocationOf(methodName)
                                                    .on("input");
                                        } else {
                                            iv = div
                                                    .onInvocationOf(methodName)
                                                    .on("input");
                                        }
                                        break;

                                }
                                return iv;
                            };
                            if (required) {
                                InvocationBuilder<?> iv = subBb.invoke("header")
                                        .withStringLiteral(headerName);
                                f.apply(iv).on("request");
                            } else {
                                subBb.invoke("ifPresent")
                                        .withLambdaArgument(whenPresent -> {
                                            whenPresent.withArgument("bldr");
                                            whenPresent
                                                    .body(whenPresentBody -> {
                                                        InvocationBuilder<?> iv = whenPresentBody.invoke("header")
                                                                .withStringLiteral(headerName);
                                                        f.apply(iv).on("request");
                                                    });
                                        })
                                        .onInvocationOf(methodName)
                                        .on("input");
                            }
                        }
                    });
                })
                .on("super");
    }

    private boolean withHeaderInputProperties(Optional<Shape> input, TriConsumer<Set<Map.Entry<String, MemberShape>>, Map<String, String>, Set<String>> c) {
        if (!input.isPresent()) {
            return false;
        }
        Set<Map.Entry<String, MemberShape>> headerMembers = new LinkedHashSet<>();
        Map<String, String> headerForMemberName = new HashMap<>();
        Set<String> requiredHeaderTraits = new HashSet<>();
        StructureShape s = input.get().asStructureShape().get();
        for (Map.Entry<String, MemberShape> m : s.getAllMembers().entrySet()) {
            m.getValue().getTrait(HttpHeaderTrait.class).ifPresent(hdr -> {
                headerMembers.add(m);
                headerForMemberName.put(m.getKey(), hdr.getValue());
                if (m.getValue().getTrait(RequiredTrait.class).isPresent()) {
                    requiredHeaderTraits.add(m.getKey());
                }
            });
        }
        c.accept(headerMembers, headerForMemberName, requiredHeaderTraits);
        return true;
    }

    private Optional<Map.Entry<String, MemberShape>> findHttpPayload(Optional<Shape> input) {
        return input.flatMap(in -> {
            for (Map.Entry<String, MemberShape> e : in.getAllMembers().entrySet()) {
                if (e.getValue().getTrait(HttpPayloadTrait.class).isPresent()) {
                    return Optional.of(e);
                }
            }
            return Optional.empty();
        });
    }

    private <T> InvocationBuilder<TypeAssignment<BlockBuilder<T>>> findInInput(
            String label, boolean queryParam, Shape input, ClassBuilder<String> cb,
            InvocationBuilder<TypeAssignment<BlockBuilder<T>>> inv) {
        if (!input.isStructureShape()) {
            throw new ConfigurationError("Not a structure shape: " + input);
        }

        if (label.isBlank()) {
            throw new ConfigurationError("Attempt to look for empty string in " + input
                    + " for " + shape);
        }

        boolean found = false;
        StructureShape shp = input.asStructureShape().get();
        Set<Map.Entry<String, MemberShape>> mems = shp.getAllMembers().entrySet();

        for (Map.Entry<String, MemberShape> m : mems) {
            if (!queryParam) {
                if (label.equals(m.getKey())) {
                    Optional<HttpLabelTrait> lbl = m.getValue().getTrait(HttpLabelTrait.class);
                    if (lbl.isPresent()) {
                        HttpLabelTrait l = lbl.get();
                        String getterMethod = escape(decapitalize(m.getKey()));
                        found = true;
                        return inv.onInvocationOf("add")
                                .withMethodReference(getterMethod)
                                .on("input");
                    }
                }
            } else {
                Optional<HttpQueryTrait> query = m.getValue().getTrait(HttpQueryTrait.class);
                if ((query.isPresent() && query.get().getValue().equals(queryParam)) || m.getKey().equals(label)) {
                    String getterMethod = escape(decapitalize(m.getKey()));

                    found = true;

                    Shape target = model.expectShape(m.getValue().getTarget());
                    boolean isRaw = "smithy.api".equals(target.getId().getNamespace());
                    boolean required = m.getValue().getTrait(RequiredTrait.class).isPresent();

                    switch (target.getType()) {
                        case BIG_DECIMAL:
                        case BIG_INTEGER:
                        case BOOLEAN:
                        case BYTE:
                        case INTEGER:
                        case SHORT:
                        case LONG:
                        case STRING:
                        case FLOAT:
                        case DOUBLE:
                        case TIMESTAMP:
                        case SET:
                        case LIST:
                            return inv.onInvocationOf("putQueryParameter")
                                    .withStringLiteral(label)
                                    .withMethodReference(getterMethod)
                                    .on("input");
                        default:
                            throw new ConfigurationError(target.getType()
                                    + "s not currently supported as URL parameters");
                    }
                }
            }
        }
        if (!found) {
            throw new ConfigurationError("Did not find a match for "
                    + (queryParam ? "QUERY '" : "LABEL '")
                    + label + "' in " + input
                    + " with members " + shp.getAllMembers().keySet());
        }

        return inv;
    }

}
