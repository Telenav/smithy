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
import com.mastfrog.java.vogon.ClassBuilder.Value;
import com.mastfrog.java.vogon.ClassBuilder.Variable;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import static com.mastfrog.smithy.generators.FeatureBridge.SWAGGER_GENERATION_PRESENT;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import com.mastfrog.smithy.generators.SmithyGenerationSettings;
import com.mastfrog.smithy.java.generators.base.AbstractJavaGenerator;
import java.nio.file.Path;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 *
 * @author Tim Boudreau
 */
public class ServeSwaggerGenerator extends AbstractJavaGenerator<ServiceShape> {

    private final SmithyGenerationSettings settings;

    public ServeSwaggerGenerator(ServiceShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language,
            SmithyGenerationSettings settings) {
        super(shape, model, destSourceRoot.resolve("../java"), target, language);
        this.settings = settings;
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        boolean hasSwagger
                = SmithyGenerationContext.get().get(SWAGGER_GENERATION_PRESENT).orElse(false);

        if (!hasSwagger) {
            System.err.println("Swagger generator not present - will not generate an HTTP endpoint for it.");
            return;
        }

        String swaggerPath = settings.getString("swaggerPath").orElse(null);
        if (swaggerPath == null) {
//            return;
            swaggerPath = "/swagger";
        }
        addTo.accept(generateSwaggerSchemaLoader());
        addTo.accept(generateSwaggerActeur(swaggerPath));

    }

    private ClassBuilder<String> generateSwaggerActeur(String swaggerPath) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape) + ".swagger")
                .named("SwaggerActeur")
                .withModifier(FINAL)
                .importing(
                        "com.mastfrog.acteur.Acteur",
                        "com.mastfrog.acteur.HttpEvent",
                        "com.mastfrog.acteur.annotations.HttpCall",
                        "static java.time.temporal.ChronoField.MILLI_OF_SECOND",
                        "static java.time.temporal.ChronoField.NANO_OF_SECOND",
                        "static com.mastfrog.acteur.header.entities.CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY",
                        "static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL",
                        "static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE",
                        "static com.mastfrog.acteur.headers.Headers.ETAG",
                        "static com.mastfrog.acteur.headers.Headers.IF_MODIFIED_SINCE",
                        "static com.mastfrog.acteur.headers.Headers.IF_NONE_MATCH",
                        "static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED",
                        "static com.mastfrog.acteur.headers.Method.GET",
                        "static com.mastfrog.acteur.headers.Method.HEAD",
                        "com.mastfrog.acteur.preconditions.Methods",
                        "com.mastfrog.acteur.preconditions.Description",
                        "com.mastfrog.acteur.preconditions.Path",
                        "static com.mastfrog.mime.MimeType.JSON_UTF_8",
                        "static io.netty.handler.codec.http.HttpResponseStatus.GONE",
                        "static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED",
                        "javax.inject.Inject")
                .extending("Acteur")
                .annotatedWith("Description", anno
                        -> anno.addArgument("value", "Serves OpenAPI API documentation")
                        .addArgument("category", "Swagger"))
                .annotatedWith("HttpCall",
                        anno -> anno.addArgument("order", Integer.MAX_VALUE))
                .annotatedWith("Path",
                        anno -> anno.addArgument("value", swaggerPath))
                .annotatedWith("Methods", anno -> anno.addExpressionArgument("value", "{GET, HEAD}"))
                .constructor(con -> {
                    con.annotatedWith("Inject").closeAnnotation()
                            .addArgument("HttpEvent", "event")
                            .addArgument("SwaggerInfo", "info")
                            .body(bb -> {
                                bb.iff().booleanExpression("!info.body().isPresent()")
                                        .lineComment("Loading swagger failed somehow - not generated into classpath?")
                                        .invoke("reply")
                                        .withArgument("GONE")
                                        .inScope()
                                        .statement("return")
                                        .endIf();
                                bb.lineComment("Test if either of the If-Modified-Since or If-None-Match headers")
                                        .lineComment("are effectively identical to the Last-Modifed or Etag headers")
                                        .lineComment("we would send (compensating for time-zones and double-quoted")
                                        .lineComment("or un-double-quoted etag)")
                                        .blankLine()
                                        .lineComment("If either matches, we should send a 304 Not Modified response and no payload.");
                                bb.declare("matched")
                                        .initializedByInvoking("orElse")
                                        .withArgument(false)
                                        .onInvocationOf("or")
                                        .withLambdaArgument(lb -> {
                                            lb.body(lbb -> {
                                                lbb.returningInvocationOf("map")
                                                        .withLambdaArgument(lbb1 -> {
                                                            lbb1.withArgument("lastModified");
                                                            lbb1.body(lb1b1 -> {
                                                                lb1b1.returningInvocationOf("orElse")
                                                                        .withArgument(false)
                                                                        .onInvocationOf("map")
                                                                        .withLambdaArgument()
                                                                        .withArgument("ifModifiedSince")
                                                                        .body(ims -> {
                                                                            ims.lineComment("Ensure we are comparing apples to apples:");
                                                                            ims.declare("modifiedMatch")
                                                                                    .initializedByInvoking("equals")
                                                                                    .withArgumentFromInvoking("with")
                                                                                    .withArgument("MILLI_OF_SECOND")
                                                                                    .withArgument(0)
                                                                                    .onInvocationOf("with")
                                                                                    .withArgument("NANO_OF_SECOND")
                                                                                    .withArgument(0)
                                                                                    .onInvocationOf("toInstant")
                                                                                    .on("ifModifiedSince")
                                                                                    .onInvocationOf("toInstant")
                                                                                    .on("lastModified")
                                                                                    .as("boolean");
                                                                            Variable v = variable("modifiedMatch");
                                                                            Value inv
                                                                                    = invocationOf("isBefore")
                                                                                            .withArgument("ifModifiedSince")
                                                                                            .on("lastModified");
                                                                            ims.returning(v.logicalOrWith(inv));
                                                                        })
                                                                        .onInvocationOf("httpHeader")
                                                                        .withArgument("IF_MODIFIED_SINCE")
                                                                        .on("event");
                                                            });
                                                        })
                                                        .onInvocationOf("lastModified")
                                                        .on("info");
                                            });
                                        })
                                        .onInvocationOf("map")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("ifNoneMatch")
                                                    .body(lbb -> {
                                                        lbb.lineComment("We only get here if the if-modified-since test didn't pan out.");
                                                        lbb.returningInvocationOf("orElse")
                                                                .withArgument(false)
                                                                .onInvocationOf("map")
                                                                .withLambdaArgument(lblb -> {
                                                                    lblb.withArgument("etag")
                                                                            .body(lbbb -> {
                                                                                lbbb.returningInvocationOf("equals").withArgument("ifNoneMatch").on("etag");
                                                                            });
                                                                })
                                                                .onInvocationOf("etag")
                                                                .on("info");
                                                    });
                                        }).onInvocationOf("httpHeader")
                                        .withArgument("IF_NONE_MATCH")
                                        .on("event")
                                        .as("boolean");
                                bb.blankLine().lineComment("If a cache header was matched, we're done.");
                                bb.iff().booleanExpression("matched")
                                        .invoke("reply")
                                        .withArgument("NOT_MODIFIED")
                                        .inScope()
                                        .statement("return")
                                        .endIf();
                                bb.blankLine().lineComment("Add our cache headers to the response.");
                                bb.invoke("ifPresent")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("zdt")
                                                    .body(lbb -> {
                                                        lbb.invoke("add")
                                                                .withArgument("LAST_MODIFIED")
                                                                .withArgument("zdt")
                                                                .inScope();
                                                    });
                                        })
                                        .onInvocationOf("lastModified")
                                        .on("info");
                                bb.invoke("ifPresent")
                                        .withLambdaArgument(lb -> {
                                            lb.withArgument("etag")
                                                    .body(lbb -> {
                                                        lbb.invoke("add")
                                                                .withArgument("ETAG")
                                                                .withArgument("etag")
                                                                .inScope();
                                                    });
                                        })
                                        .onInvocationOf("etag")
                                        .on("info");
                                bb.blankLine().lineComment("Ensure we send the correct application/json content type.");
                                bb.invoke("add")
                                        .withArgument("CONTENT_TYPE")
                                        .withArgument("JSON_UTF_8")
                                        .inScope();
                                bb.blankLine().lineComment("Include a Cache-Control header so the caller knows")
                                        .lineComment("how long the response can be cached.");
                                bb.invoke("add")
                                        .withArgument("CACHE_CONTROL")
                                        .withArgument("PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY")
                                        .inScope();
                                bb.blankLine().lineComment("In the special case that this is a HEAD request")
                                        .lineComment("we want to bail out before sending the payload.");
                                bb.iff(invocationOf("is")
                                        .withArgument("HEAD")
                                        .onInvocationOf("method").on("event"))
                                        .invoke("ok").inScope()
                                        .statement("return")
                                        .endIf();
                                bb.blankLine().lineComment("Send the response and we're done.");
                                bb.invoke("ok")
                                        .withArgumentFromInvoking("get")
                                        .onInvocationOf("body")
                                        .on("info")
                                        .inScope();
                            });
                });

        return cb;
    }

    public ClassBuilder<String> generateSwaggerSchemaLoader() {
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape) + ".swagger")
                .named("SwaggerInfo")
                .withModifier(FINAL)
                .importing(
                        "java.io.ByteArrayOutputStream",
                        "java.io.IOException",
                        "java.io.InputStream",
                        "java.security.MessageDigest",
                        "java.security.NoSuchAlgorithmException",
                        "java.time.ZoneId",
                        "java.time.ZonedDateTime",
                        "java.time.temporal.ChronoField",
                        "java.util.Base64",
                        "java.util.Optional",
                        "javax.inject.Singleton"
                ).annotatedWith("Singleton").closeAnnotation()
                .docComment("Singleton object which loads the swagger json on instantiation and"
                        + " can provide it to SwaggerActeur, along with cache header information,"
                        + " to serve it.");
        cb.field("SERVER_START", fld -> {
            fld.withModifier(PRIVATE, STATIC, FINAL)
                    .initializedFromInvocationOf("withZoneSameInstant")
                    .withArgumentFromInvoking("of").withStringLiteral("Z")
                    .on("ZoneId")
                    .onInvocationOf("with")
                    .withArgumentFromField("NANO_OF_SECOND").of("ChronoField")
                    .withArgument(0)
                    .onInvocationOf("with")
                    .withArgumentFromField("MILLI_OF_SECOND").of("ChronoField")
                    .withArgument(0)
                    .onInvocationOf("now")
                    .on("ZonedDateTime")
                    .ofType("ZonedDateTime");
        });
        cb.field("bytes").withModifier(PRIVATE, FINAL).ofType("byte[]");
        cb.field("etag").withModifier(PRIVATE, FINAL).ofType("String");

        cb.constructor(con -> {
            con.body(bb -> {
                bb.declare("in")
                        .initializedByInvoking("getResourceAsStream")
                        .withStringLiteral("swagger.json")
                        .on(cb.className() + ".class")
                        .as("InputStream");
                bb.lineComment("If the input stream is null, then there is no swagger.json next")
                        .lineComment("to this class on the classpath.  That could happen if swagger")
                        .lineComment("generation was disabled, or the project was built unusually somehow;")
                        .lineComment("(IDE issues) - do not treat that as catastrophic.");
                bb.ifNull("in")
                        .assignField("bytes")
                        .ofThis()
                        .toExpression("null")
                        .assignField("etag")
                        .ofThis()
                        .toExpression("null")
                        .statement("return")
                        .endIf();
                bb.declare("bytesLocal").initializedWith("null")
                        .as("byte[]");
                bb.declare("etagLocal").initializedWith("null")
                        .as("String");
                bb.trying(try1 -> {
                    try1.lineComment("Read the stream the old-fashioned way:");
                    try1.trying(try2 -> {
                        try2.declare("out")
                                .initializedWithNew(nb -> nb.ofType("ByteArrayOutputStream"))
                                .as("ByteArrayOutputStream");
                        try2.declare("buffer").initializedWith("new byte[512]")
                                .as("byte[]");
                        try2.declare("read").as("int");
                        try2.whileLoop(whyle -> {
                            whyle.invoke("write")
                                    .withArgument("buffer")
                                    .withArgument(0)
                                    .withArgument("read")
                                    .on("out");
                            whyle.underCondition().booleanExpression("((read = in.read(buffer)) > 0)");
                        });
                        try2.assign("bytesLocal")
                                .toInvocation("toByteArray")
                                .on("out");
                        try2.lineComment("Compute a hash for the Etag header we'll serve:");
                        try2.declare("dig")
                                .initializedByInvoking("getInstance")
                                .withStringLiteral("SHA-1")
                                .on("MessageDigest")
                                .as("MessageDigest");
                        try2.declare("hash")
                                .initializedByInvoking("digest")
                                .withArgument("bytesLocal")
                                .on("dig")
                                .as("byte[]");
                        try2.assign("etagLocal")
                                .toInvocation("encodeToString")
                                .withArgument("hash")
                                .onInvocationOf("getUrlEncoder")
                                .on(("Base64"));
                        try2.fynalli(fi -> {
                            fi.invoke("close").on("in");
                        });
                    });
                    try1.catching(cat -> {
                        cat.invoke("println")
                                .withStringLiteral("Failed reading swaggger file")
                                .onField("err").of("System");
                    }, "IOException", "NoSuchAlgorithmException");
                });
                bb.ifNull("bytesLocal")
                        .assignField("bytes").ofThis().toExpression("null")
                        .assignField("etag").ofThis().toExpression("null")
                        .orElse()
                        .assignField("bytes").ofThis().toExpression("bytesLocal")
                        .assignField("etag").ofThis().toExpression("etagLocal")
                        .endIf();
            });
            cb.method("lastModified")
                    .docComment("Get the effective last-modified date of the swagger data; "
                            + "the only safe value to return is the server startup time, since "
                            + "what we will serve cannot have changed since then."
                            + "@return an optional which is empty if there was no swagger file, and "
                            + "otherwise contains the server start time")
                    .returning("Optional<ZonedDateTime>")
                    .body(bb -> {
                        bb.ifNull("bytes")
                                .returningInvocationOf("empty")
                                .on("Optional")
                                .endIf();
                        bb.returningInvocationOf("of")
                                .withArgument("SERVER_START")
                                .on("Optional");
                    });
            cb.method("etag")
                    .docComment("Get a sha-1 hash of the content as an etag, if there is any content."
                            + "\n@return an Optional populated if there is swagger json to serve")
                    .returning("Optional<String>")
                    .body(bb -> {
                        bb.returningInvocationOf("ofNullable")
                                .withArgument("etag")
                                .on("Optional");
                    });
            cb.method("body")
                    .docComment("Get the raw UTF-8 bytes of the content, if there is any."
                            + "\n@return the swagger JSON, if present")
                    .returning("Optional<byte[]>")
                    .body(bb -> {
                        bb.returningInvocationOf("ofNullable")
                                .withArgument("bytes")
                                .on("Optional");
                    });
        });
        return cb;
    }

}
