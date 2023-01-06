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

import com.telenav.smithy.server.common.AbstractMarkupClassGenerator;
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
