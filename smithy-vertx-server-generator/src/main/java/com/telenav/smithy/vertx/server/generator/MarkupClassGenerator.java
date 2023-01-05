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
package com.telenav.smithy.vertx.server.generator;

import com.mastfrog.smithy.server.common.AbstractMarkupClassGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;

/**
 *
 * @author Tim Boudreau
 */
public final class MarkupClassGenerator extends AbstractMarkupClassGenerator {

    public MarkupClassGenerator(String eventClassName) {
        super(eventClassName);
    }

    @Override
    protected void frameworkImports(ClassBuilder<String> cb) {
        cb.importing("io.vertx.core.http.HttpServerResponse",
                "io.vertx.ext.web.RoutingContext");
    }

    protected <T> void specifyCacheHeaderMatchArguments(MethodBuilder<T> m) {
        m.addArgument(eventClassName, "event");
    }

    protected <T, B extends BlockBuilderBase<T, B, X>, X> void applyHeaders(String etagVarName, B bb) {
        bb.declare("resp").initializedByInvoking("response").on("event").as("HttpServerResponse");
        bb.invoke("putHeader").withArgumentFromField("ETAG").of("HttpHeaderNames")
                .withArgument(etagVarName).on("resp");
        bb.invoke("putHeader").withArgumentFromField("LAST_MODIFIED").of("HttpHeaderNames")
                .withArgumentFromInvoking("get").withArgument("path").on("timeStrings").on("resp");
        bb.invoke("putHeader").withArgumentFromField("CACHE_CONTROL").of("HttpHeaderNames")
                .withStringLiteral(cacheControl()).on("resp");
    }

    protected <T, B extends BlockBuilderBase<T, B, X>, X> void fetchIfNoneMatchHeader(String varName, B bb) {
        bb.declare(varName)
                .initializedByInvoking("getHeader")
                .withArgumentFromField("IF_NONE_MATCH")
                .of("HttpHeaderNames")
                .onInvocationOf("request")
                .on("event").as("String");
    }

    protected <T, B extends BlockBuilderBase<T, B, X>, X> void fetchIfModifiedSinceHeader(String varName, B bb) {
        bb.declare(varName)
                .initializedByInvoking("getHeader")
                .withArgumentFromField("IF_MODIFIED_SINCE")
                .of("HttpHeaderNames")
                .onInvocationOf("request")
                .on("event").as("String");

    }
}
