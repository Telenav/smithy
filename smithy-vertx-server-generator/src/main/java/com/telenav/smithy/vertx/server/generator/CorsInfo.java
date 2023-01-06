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
package com.telenav.smithy.vertx.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.strings.Strings;
import com.telenav.smithy.utils.path.PathInfo;
import com.telenav.smithy.utils.path.PathInformationExtractor;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.CorsTrait;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 *
 * @author Tim Boudreau
 */
final class CorsInfo {

    private static final String EXPOSED_HEADERS_VAR = "exposedHeaders";
    private static final String ALLOWED_HEADERS_VAR = "allowedHeaders";
    // Note, we want to explicitly parameterize on TreeSet - if this code
    // were changed to use an unsorted hash set, the variable names would be
    // unpredictable, and wa aim always to generate identical code for identical
    // input.
    private final Map<String, TreeSet<String>> paths = new TreeMap<>();
    private final Map<String, TreeSet<String>> regexen = new TreeMap<>();
    private final Map<OperationShape, PathInfo> pathInfoForOperation = new HashMap<>();
    private final Map<OperationShape, HttpTrait> httpTraitForOperation = new HashMap<>();
    private final Set<TreeSet<String>> methodSets = new TreeSet<>(CorsInfo::compareTreeSets);
    private final CorsTrait cors;
    private final Collection<? extends OperationShape> ops;

    CorsInfo(Model model, ServiceShape shape, Collection<? extends OperationShape> ops) {
        this.ops = ops;
        cors = shape.getTrait(CorsTrait.class).map(cors -> {
            for (OperationShape op : ops) {
                op.getTrait(HttpTrait.class).ifPresent(http -> {
                    StructureShape inputShape = op.getInput().map(model::expectShape).flatMap(Shape::asStructureShape).orElse(null);
                    PathInfo info = new PathInformationExtractor(model, http.getUri()).extractPathInfo(inputShape);
                    if (info.isRegex()) {
                        regexen.computeIfAbsent(info.text(), tx -> new TreeSet<>()).add(http.getMethod());
                    } else {
                        paths.computeIfAbsent(info.text(), tx -> new TreeSet<>()).add(http.getMethod());
                    }
                    pathInfoForOperation.put(op, info);
                    httpTraitForOperation.put(op, http);
                });
            }
            methodSets.addAll(paths.values());
            methodSets.addAll(regexen.values());
            return cors;
        }).orElse(null);
    }

    static int compareTreeSets(TreeSet<String> o1, TreeSet<String> o2) {
        return o1.toString().compareTo(o2.toString());
    }

    boolean isCors() {
        return cors != null;
    }

    boolean ifCors(Consumer<CorsInfo> run) {
        if (isCors()) {
            run.accept(this);
            return true;
        }
        return false;
    }

    Optional<String> corsHandlerVariableForOperation(OperationShape op) {
        PathInfo info = pathInfoForOperation.get(op);
        if (info != null) {
            TreeSet<String> methodSet;
            if (info.isRegex()) {
                methodSet = regexen.get(info.text());
            } else {
                methodSet = paths.get(info.text());
            }
            return Optional.of(corsHandlerVariableForMethodSet(methodSet));
        }
        return Optional.empty();
    }

    String corsHandlerVariableForMethodSet(TreeSet<String> set) {
        return "cors_" + Strings.join('_', set).toLowerCase();
    }

    private void ensureImports(ClassBuilder<?> cb) {
        cb.importing("java.util.Set", "java.util.HashSet", "java.util.Arrays", "java.util.Collections");
    }

    public <C, X, B extends ClassBuilder.BlockBuilderBase<C, B, X>> void declareAllowedHeaders(ClassBuilder<?> cb, B bb) {
        ensureImports(cb);
        bb.blankLine().lineComment("Allowed headers defined in the @cors trait on the service").lineComment("These will be in the access-control-allowed-headers on responses");
        bb.declare(ALLOWED_HEADERS_VAR).initializedByInvoking("unmodifiableSet").withArgumentFromNew(nb -> {
            nb.withArgumentFromInvoking("asList", ib -> {
                for (String h : cors.getAdditionalAllowedHeaders()) {
                    ib.withStringLiteral(h);
                }
                ib.on("Arrays");
            }).ofType("HashSet<>");
        }).on("Collections").as("Set<String>");
    }

    public <C, X, B extends ClassBuilder.BlockBuilderBase<C, B, X>> void declareExposedHeaders(ClassBuilder<?> cb, B bb) {
        ensureImports(cb);
        bb.blankLine().lineComment("Exposed headers defined in the @cors trait on the service").lineComment("These will be in the access-control-allowed-headers on responses");
        bb.declare(EXPOSED_HEADERS_VAR).initializedByInvoking("unmodifiableSet").withArgumentFromNew(nb -> {
            nb.withArgumentFromInvoking("asList", ib -> {
                for (String h : cors.getAdditionalExposedHeaders()) {
                    ib.withStringLiteral(h);
                }
                ib.on("Arrays");
            }).ofType("HashSet<>");
        }).on("Collections").as("Set<String>");
    }

    public <C, X, B extends ClassBuilder.BlockBuilderBase<C, B, X>> void generateCorsHandlerDeclarations(ClassBuilder<?> cb, B bb) {
        cb.importing(
                "io.vertx.core.http.HttpMethod",
                "io.vertx.ext.web.handler.CorsHandler"
        //                "com.telenav.smithy.vertx.debug.CorsHandler"
        );
        bb.blankLine().lineComment("Create cors handlers for the exact sets of methods").lineComment("handled by different paths:");
        for (TreeSet<String> set : methodSets) {
            String varName = corsHandlerVariableForMethodSet(set);
            bb.declare(varName)
                    .initializedByInvoking("wrap")
                    .withArgumentFromInvoking("addOrigin").withStringLiteral(cors.getOrigin()).onInvocationOf("maxAgeSeconds").withArgument(cors.getMaxAge()).onInvocationOf("exposedHeaders").withArgument(EXPOSED_HEADERS_VAR).onInvocationOf("allowedHeaders").withArgument(ALLOWED_HEADERS_VAR).onInvocationOf("allowedMethods").withArgumentFromNew(nb -> {
                nb.withArgumentFromInvoking("asList", ib -> {
                    for (String m : set) {
                        ib.withArgumentFromField(m.toUpperCase()).of("HttpMethod");
                    }
                    ib.on("Arrays");
                }).ofType("HashSet<>");
            }).onInvocationOf("allowCredentials").withArgument(true)
                    .onInvocationOf("allowPrivateNetwork").withArgument(true)
                    .onInvocationOf("create").on("CorsHandler")
                    .onInvocationOf("scope")
                    .on("result")
                    .as("Handler<RoutingContext>");
        }
    }

    public <C, X, B extends ClassBuilder.BlockBuilderBase<C, B, X>> void applyCorsHandlers(B bb, String verticleBuilderName) {
        bb.blankLine().lineComment("CORS handlers for OPTIONS requests");
        Set<String> seenPaths = new HashSet<>();
        for (OperationShape op : ops) {
            PathInfo info = pathInfoForOperation.get(op);
            if (info != null) {
                if (!seenPaths.add(info.text())) {
                    continue;
                }
                corsHandlerVariableForOperation(op).ifPresent(corsHandler -> {
                    ClassBuilder.InvocationBuilder<B> inv = bb.invoke("handledBy").withArgument(corsHandler);
                    if (info.isRegex()) {
                        inv = inv.onInvocationOf("withRegex").withStringLiteral(info.text());
                    } else {
                        inv = inv.onInvocationOf("withPath").withStringLiteral(info.text());
                    }
                    inv.onInvocationOf("forHttpMethod").withArgument("HttpMethod.OPTIONS").onInvocationOf("route").on(verticleBuilderName);
                });
            }
        }
    }

}
