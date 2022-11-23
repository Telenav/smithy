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
            swaggerPath = "/swagger";
        }
        addTo.accept(generateSwaggerSchemaLoader());
        addTo.accept(generateSwaggerActeur(swaggerPath));
    }

    private ClassBuilder<String> generateSwaggerActeur(String swaggerPath) {
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape) + ".swagger")
                .named("SwaggerActeur");
        cb.withModifier(FINAL)
                .importing(
                        "com.mastfrog.acteur.Acteur",
                        "com.mastfrog.acteur.CheckIfModifiedSinceHeader",
                        "com.mastfrog.acteur.CheckIfNoneMatchHeader",
                        "com.mastfrog.acteur.HttpEvent",
                        "com.mastfrog.acteur.annotations.Concluders",
                        "com.mastfrog.acteur.annotations.HttpCall",
                        "com.mastfrog.acteur.preconditions.Description",
                        "com.mastfrog.acteur.preconditions.Methods",
                        "com.mastfrog.acteur.preconditions.Path",
                        "javax.inject.Inject",
                        "static com.mastfrog.acteur.header.entities.CacheControl.PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY",
                        "static com.mastfrog.acteur.headers.Headers.CACHE_CONTROL",
                        "static com.mastfrog.acteur.headers.Headers.CONTENT_LENGTH",
                        "static com.mastfrog.acteur.headers.Headers.CONTENT_TYPE",
                        "static com.mastfrog.acteur.headers.Headers.ETAG",
                        "static com.mastfrog.acteur.headers.Headers.LAST_MODIFIED",
                        "static com.mastfrog.acteur.headers.Method.GET",
                        "static com.mastfrog.acteur.headers.Method.HEAD",
                        "static com.mastfrog.mime.MimeType.JSON_UTF_8",
                        "static io.netty.handler.codec.http.HttpResponseStatus.GONE"
                )
                .extending("Acteur")
                .annotatedWith("Description", anno
                        -> anno.addArgument("value", "Serves OpenAPI API documentation generated from the Smithy model.")
                        .addArgument("category", "Swagger"))
                .annotatedWith("HttpCall",
                        anno -> anno.addArgument("order", Integer.MAX_VALUE))
                .annotatedWith("Path",
                        anno -> anno.addArgument("value", swaggerPath))
                .annotatedWith("Methods", anno -> anno.addExpressionArgument("value", "{GET, HEAD}"))
                .annotatedWith("Concluders", anno -> {
                    // These run AFTER our acteur to actually send a response
                    anno.addArrayArgument("value", arr -> {
                        arr.expression("CheckIfNoneMatchHeader.class")
                                .expression("CheckIfModifiedSinceHeader.class")
                                .expression(cb.className() + ".SendSwaggerPayload.class");
                    });
                })
                .innerClass("SendSwaggerPayload", inner -> {
                    inner.extending("Acteur")
                            .withModifier(STATIC, FINAL)
                            .docComment("Concluder acteur that actually sends the payload, if CheckIfModifiedSinceHeader "
                                    + "and CheckIfModifiedSinceHeader did not detect matching HTTP cache headers and short-circuit "
                                    + "with a <code>304 Not Modified</code> response")
                            .annotatedWith("Description").withValue("Sends the Swagger JSON payload.")
                            .constructor(con -> {
                                con.annotatedWith("Inject").closeAnnotation()
                                        .addArgument("SwaggerInfo", "info")
                                        .body(bb -> {
                                            bb.lineComment("We know the size, no need for chunked transport.");
                                            bb.invoke("setChunked")
                                                    .withArgument(false)
                                                    .inScope();
                                            bb.declare("bytes")
                                                    .initializedByInvoking("get")
                                                    .onInvocationOf("body")
                                                    .on("info")
                                                    .as("byte[]");
                                            bb.blankLine().lineComment("Set the content length header.");
                                            bb.invoke("add")
                                                    .withArgument("CONTENT_LENGTH")
                                                    .withArgumentFromField("length").of("bytes")
                                                    .inScope();
                                            bb.blankLine().lineComment("And send the payload");
                                            bb.invoke("ok")
                                                    .withArgument("bytes")
                                                    .inScope();
                                        });
                            });
                })
                .constructor(con -> {
                    con.annotatedWith("Inject").closeAnnotation()
                            .addArgument("HttpEvent", "event")
                            .addArgument("SwaggerInfo", "info")
                            .body(bb -> {
                                bb.iff().booleanExpression("!info.body().isPresent()")
                                        .lineComment("In an IDE? The swagger.json was not generated.")
                                        .invoke("reply")
                                        .withArgument("GONE")
                                        .inScope()
                                        .statement("return")
                                        .endIf();
                                bb.blankLine().lineComment("Set the Last-Modified HTTP cache header");
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
                                bb.blankLine().lineComment("Set the ETag HTTP cache header");
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
                                bb.invoke("add")
                                        .withArgument("CONTENT_TYPE")
                                        .withArgument("JSON_UTF_8")
                                        .inScope();
                                bb.blankLine().lineComment("Cache headers are not much use if we don't tell the")
                                        .lineComment("client how it should use them.  This defines that clients")
                                        .lineComment("should always phone-home to check if it has changed, and discard")
                                        .lineComment("the content after one day.");
                                bb.invoke("add")
                                        .withArgument("CACHE_CONTROL")
                                        .withArgument("PUBLIC_MUST_REVALIDATE_MAX_AGE_1_DAY")
                                        .inScope();
                                bb.blankLine().lineComment("If this is a HEAD request, we're done");
                                bb.iff(invocationOf("is")
                                        .withArgument("HEAD")
                                        .onInvocationOf("method").on("event"))
                                        .lineComment("No payload, just send an OK for HEAD - @Concluders will not run")
                                        .invoke("ok").inScope()
                                        .statement("return")
                                        .endIf();
                                bb.blankLine().lineComment("We do NOT send the response here - SendSwaggerPayload will do that")
                                        .lineComment("*after* CheckIfNoneMatchHeader and CheckIfModifiedSinceHeader get a")
                                        .lineComment("chance to send a 304 response if the client already has an identical")
                                        .lineComment("cached payload.");
                                bb.invoke("next").inScope();
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
                ).annotatedWith("Singleton").closeAnnotation();
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
                        /*
                    MessageDigest dig = MessageDigest.getInstance("MD5");
                    byte[] hash = dig.digest(bytesLocal);
                    etagLocal = Base64.getUrlEncoder().encodeToString(hash);
                         */
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
                    .returning("Optional<String>")
                    .body(bb -> {
                        bb.returningInvocationOf("ofNullable")
                                .withArgument("etag")
                                .on("Optional");
                    });
            cb.method("body")
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