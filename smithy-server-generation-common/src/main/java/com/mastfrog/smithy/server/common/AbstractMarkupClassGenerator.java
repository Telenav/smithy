/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import com.mastfrog.smithy.generators.SmithyGenerationContext;
import java.util.function.BiConsumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractMarkupClassGenerator implements BiConsumer<String, ClassBuilder<String>> {

    public static final String SETTINGS_KEY_CACHE_CONTROL = "markup-cache-control-header";
    private static final String DEFAULT_CACHE_CONTROL = "public,must-revalidate,max-age=1800";

    protected final String eventClassName;

    protected AbstractMarkupClassGenerator(String eventClassName) {
        this.eventClassName = eventClassName;
    }

    protected String cacheControl() {
        return SmithyGenerationContext.get().settings()
                .getString(SETTINGS_KEY_CACHE_CONTROL)
                .orElse(DEFAULT_CACHE_CONTROL);
    }

    protected boolean inboundPathsHaveLeadingSlash() {
        return true;
    }

    @Override
    public void accept(String serviceShapeId, ClassBuilder<String> markup) {
        boolean slash = inboundPathsHaveLeadingSlash();

        markup.docComment("Unpacks, hashes and provides cache-header supporting information for",
                "bundled markup files in markup.zip adjacent to this class on the classpath.");

        markup.withModifier(PUBLIC, FINAL)
                .annotatedWith("Singleton").closeAnnotation()
                .importing("javax.inject.Singleton",
                        "java.io.InputStream",
                        "java.io.OutputStream",
                        "java.nio.file.Files",
                        "java.nio.file.Path",
                        "java.nio.file.Paths",
                        "static java.nio.file.StandardOpenOption.CREATE",
                        "static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING",
                        "static java.nio.file.StandardOpenOption.WRITE",
                        "java.util.Map",
                        "java.util.HashMap",
                        "java.util.concurrent.ThreadLocalRandom",
                        "java.util.zip.ZipEntry",
                        "java.util.zip.ZipInputStream",
                        "java.security.MessageDigest",
                        "java.util.Base64",
                        "java.time.ZoneId",
                        "java.time.ZonedDateTime",
                        "java.time.format.DateTimeFormatter",
                        "java.time.format.DateTimeParseException",
                        "java.time.temporal.ChronoField",
                        "io.netty.handler.codec.http.HttpHeaderNames"
                );
        frameworkImports(markup);

        markup.field("names", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .docComment("Maps URI paths to the SHA-1 hash of the file")
                    .initializedWithNew(nb -> {
                        nb.ofType("HashMap<>");
                    }).ofType("Map<String, String>");
        });

        markup.field("etagQuoted", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .docComment("Maps URI paths to the SHA-1 hash of the file wrapped "
                            + "in quotes, for performance - etags are commonly quoted "
                            + "and are served that way, but the server should respond "
                            + "correctly to quoted and unquoted.")
                    .initializedWithNew(nb -> {
                        nb.ofType("HashMap<>");
                    }).ofType("Map<String, String>");
        });

        markup.field("times", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .docComment("Last modified times, so they do not require a disk access,"
                            + " derived from the original times in the file.")
                    .initializedWithNew(nb -> {
                        nb.ofType("HashMap<>");
                    }).ofType("Map<String, ZonedDateTime>");
        });

        markup.field("timeStrings", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .docComment("File times pre-cooked into HTTP header format.")
                    .initializedWithNew(nb -> {
                        nb.ofType("HashMap<>");
                    }).ofType("Map<String, String>");
        });

        markup.field("uid", fld -> {
            fld.withModifier(PRIVATE, FINAL);
            fld.initializedFromInvocationOf("toString")
                    .withArgumentFromInvoking("currentTimeMillis")
                    .on("System")
                    .withArgument(36)
                    .on("Long").ofType("String");
        });
        markup.field("tmp", fld -> {
            fld.withModifier(PRIVATE, FINAL).ofType("Path");
        });

        markup.constructor(con -> {
            con.throwing("Exception");
            con.body(bb -> {
                bb.lineComment("Create a new temporary directory for the markup, to ")
                        .lineComment("avoid collisons.");
                bb.assignField("tmp")
                        .ofThis()
                        .toInvocation("resolve")
                        .withStringConcatentationArgument(serviceShapeId + "Markup_")
                        .appendField("uid").ofThis().endConcatenation()
                        .onInvocationOf("get")
                        .withArgumentFromInvoking("getProperty")
                        .withStringLiteral("java.io.tmpdir")
                        .on("System")
                        .on("Paths");

                bb.invoke("createDirectories")
                        .withArgumentFromField("tmp").ofThis()
                        .on("Files");
                bb.blankLine().lineComment("Load the markup zip file; if absent, it may be")
                        .lineComment("because the model were built with markup serving disabled,")
                        .lineComment("so do not fail catastrophically in that case.");
                bb.declare("in").initializedByInvoking("getResourceAsStream")
                        .withStringLiteral("markup.zip")
                        .on(markup.className() + ".class")
                        .as("InputStream");
                ClassBuilder.IfBuilder<?> iff = bb.ifNotNull("in");
                iff.trying(tri -> {
                    tri.declare("zip")
                            .initializedWithNew().withArgument("in").ofType("ZipInputStream")
                            .as("ZipInputStream");
                    tri.declare("en").as("ZipEntry");
                    tri.whileLoop((ClassBuilder.WhileBuilder<?> loop) -> {

                        loop.declare("name").initializedByInvoking("getName")
                                .on("en").as("String");

                        String mapKeyVar;
                        if (slash) {
                            loop.declare("key").initializedWith("'/' + name").as("String");
                            mapKeyVar = "key";
                        } else {
                            mapKeyVar = "name";
                        }

                        loop.declare("file").initializedByInvoking("resolve")
                                .withArgument("name").onField("tmp").ofThis().as("Path");

                        loop.iff().booleanExpression("!Files.exists(file.getParent())")
                                .invoke("createDirectories")
                                .withArgumentFromInvoking("getParent")
                                .on("file")
                                .on("Files")
                                .endIf();

                        loop.declare("out").initializedByInvoking("newOutputStream")
                                .withArgument("file")
                                .withArgument("CREATE")
                                .withArgument("WRITE")
                                .withArgument("TRUNCATE_EXISTING")
                                .on("Files")
                                .as("OutputStream");
                        loop.trying(tri2 -> {
                            tri2.declare("bytes")
                                    .initializedByInvoking("readAllBytes")
                                    .on("zip")
                                    .as("byte[]");

                            tri2.invoke("write")
                                    .withArgument("bytes")
                                    .on("out");

                            tri2.blankLine().lineComment("Get a SHA-1 hash for use in the etag header.");
                            tri2.declare("hash")
                                    .initializedByInvoking("encodeToString")
                                    .withArgumentFromInvoking("digest")
                                    .withArgument("bytes")
                                    .onInvocationOf("getInstance")
                                    .withStringLiteral("SHA-1")
                                    .on("MessageDigest")
                                    .onInvocationOf("getEncoder")
                                    .on("Base64")
                                    .as("String");

                            tri2.invoke("put")
                                    .withArgument(mapKeyVar)
                                    .withArgument("hash")
                                    .onField("names").ofThis();

                            tri2.invoke("put")
                                    .withArgument(mapKeyVar)
                                    .withStringConcatentationArgument("\"")
                                    .appendExpression("hash")
                                    .append('"')
                                    .endConcatenation()
                                    .onField("etagQuoted").ofThis();

                            tri2.blankLine().lineComment("Store the file time both in parsed and header form.")
                                    .lineComment("It is critically important that these be stored with zeroed ")
                                    .lineComment("milliseconds, or tests will fail on inbound headers")
                                    .lineComment("that do not have any, if compared against a value that does.");
                            tri2.declare("zdt")
                                    .initializedByInvoking("ofInstant")
                                    .withArgumentFromInvoking("with")
                                    .withArgumentFromField("MILLI_OF_SECOND").of("ChronoField")
                                    .withArgument(0)
                                    .onInvocationOf("toInstant")
                                    .onInvocationOf("getLastModifiedTime")
                                    .on("en")
                                    .withArgumentFromInvoking("of")
                                    .withStringLiteral("GMT")
                                    .on("ZoneId")
                                    .on("ZonedDateTime")
                                    .as("ZonedDateTime");

                            tri2.invoke("put")
                                    .withArgument(mapKeyVar)
                                    .withArgument("zdt")
                                    .on("times");

                            tri2.invoke("put")
                                    .withArgument(mapKeyVar)
                                    .withArgumentFromInvoking("format")
                                    .withArgument("zdt")
                                    .onField("RFC_1123_DATE_TIME")
                                    .of("DateTimeFormatter")
                                    .on("timeStrings");

                            tri2.fynalli().invoke("close").on("out").endBlock();
                        });

                        loop.lineComment("Set last modified time to match the timestamp from the zip file")
                                .lineComment("(which will be correct)");
                        loop.invoke("setLastModifiedTime")
                                .withArgument("file")
                                .withArgumentFromInvoking("getLastModifiedTime")
                                .on("en")
                                .on("Files");

                        loop.underCondition().booleanExpression("(en = zip.getNextEntry()) != null");
                    });
                    tri.fynalli().invoke("close").on("in").endBlock();
                });
                iff.endIf();
                /*
                bb.invoke("println")
                        .withStringConcatentationArgument("NAMES: ")
                        .appendExpression("names")
                        .endConcatenation()
                        .onField("out").of("System");
                bb.invoke("println")
                        .withStringConcatentationArgument("TIMES: ")
                        .appendExpression("times")
                        .endConcatenation()
                        .onField("out").of("System");
                 */
            });
        });

        markup.method("hasMarkupFile")
                .withModifier(PUBLIC)
                .addArgument("String", "path")
                .returning("boolean")
                .body(bb -> {
                    /*
                    bb.invoke("println")
                            .withStringConcatentationArgument("CHECK: ")
                            .appendExpression("path")
                            .append(" result ")
                            .appendInvocationOf("containsKey")
                            .withArgument("path")
                            .on("names")
                            .append(" in ")
                            .appendInvocationOf("keySet").on("names")
                            .endConcatenation()
                            .onField("out").of("System");
                     */
                    bb.returningInvocationOf("containsKey")
                            .withArgument("path")
                            .on("names");
                });

        generateIsCacheHeaderMatchMethod(markup);

        markup.overridePublic("toString")
                .returning("String")
                .body().returningInvocationOf("toString")
                .on("names").endBlock();

        markup.method("markupDir")
                .returning("Path")
                .withModifier(PUBLIC)
                .bodyReturning("tmp");

    }

    protected void generateIsCacheHeaderMatchMethod(ClassBuilder<String> markup) {
        markup.method("isCacheHeaderMatch", mth -> {
            mth.docComment("Determines if the If-None-Match or If-Modified-Since headers match "
                    + "the cache headers in the request, in which case a 304 Not Modified response "
                    + "is appropriate.");
            mth.withModifier(PUBLIC)
                    .returning("boolean");
            specifyCacheHeaderMatchArguments(mth);
            mth.addArgument("String", "path");
            mth.body(bb -> {
                fetchIfNoneMatchHeader("ifNoneMatch", bb);
                bb.declare("defaultEtag")
                        .initializedByInvoking("get")
                        .withArgument("path")
                        .on("etagQuoted")
                        .as("String");

                bb.lineComment("Test the matching header for the ETag both quoted and unquoted.");
                IfBuilder<?> inmTest = bb.iff().booleanExpression(
                        "ifNoneMatch != null && (ifNoneMatch.equals(defaultEtag) || ifNoneMatch.equals(names.get(path)))");
//                applyHeaders("defaultEtag", inmTest);
                inmTest.returning(true).endIf();

                fetchIfModifiedSinceHeader("ifModifiedSince", bb);
                ClassBuilder.IfBuilder<?> test = bb.ifNotNull("ifModifiedSince");

                test.lineComment("The common case is that the client sends us back exactly the string")
                        .lineComment("returned by a previous request, so a string equality test is the ")
                        .lineComment("fast path here.");
                IfBuilder<?> imsTest = test.iff().booleanExpression("timeStrings.get(path).equals(ifModifiedSince)");
//                applyHeaders("defaultEtag", imsTest);
                imsTest.returning(true).endIf();

                test.trying(tri -> {
                    tri.lineComment("Do the comprehensive test");
                    tri.declare("zdt")
                            .initializedByInvoking("parse")
                            .withArgument("ifModifiedSince")
                            .withArgumentFromField("RFC_1123_DATE_TIME")
                            .of("DateTimeFormatter")
                            .on("ZonedDateTime").as("ZonedDateTime");

                    tri.declare("time").initializedByInvoking("get")
                            .withArgument("path")
                            .on("times").as("ZonedDateTime");

                    tri.iff(invocationOf("equals").withArgument("time").on("zdt")
                            .logicalOrWith(invocationOf("isAfter").withArgument("time").on("zdt")))
                            .returning(true).endIf();

                    tri.catching(cat -> {
                        cat.lineComment("We are not using the most permissive parser - let");
                        cat.lineComment("the request go forward.");
                    }, "DateTimeParseException");
                });
                test.endIf();
                applyHeaders("defaultEtag", bb);
                bb.returning(false);
            });
        });
    }

    protected abstract void frameworkImports(ClassBuilder<String> cb);

    protected abstract <T> void specifyCacheHeaderMatchArguments(ClassBuilder.MethodBuilder<T> m);

    protected abstract <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void applyHeaders(String etagVarName, B bb);

    protected abstract <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void fetchIfNoneMatchHeader(String varName, B bb);

    protected abstract <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void fetchIfModifiedSinceHeader(String varName, B bb);

}
