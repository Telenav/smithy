/*
 * Copyright 2023 Mastfrog Technologies.
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

package com.telenav.smithy.simple.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.telenav.smithy.server.common.AbstractMarkupClassGenerator;

/**
 *
 * @author Tim Boudreau
 */
public class ActeurMarkupClassGenerator extends AbstractMarkupClassGenerator {

    public ActeurMarkupClassGenerator() {
        super("HttpEvent");
    }
    
    @Override
    protected void frameworkImports(ClassBuilder<String> cb) {
//        cb.importing("com.mastfrog.acteur.HttpEvent", "com.mastfrog.acteur.Response",
//                "com.mastfrog.acteur.headers.Headers");

        cb.importing("javax.inject.Provider", "java.io.File");
        cb.implementing("Provider<File>");
        cb.overridePublic("get", mth -> {
            /*'
    @Override
    public File get() {
        return markupDir().toFile();
    }
            */
            mth.returning("File").bodyReturning("markupDir().toFile()");
        });
    }

    @Override
    protected <T> void specifyCacheHeaderMatchArguments(ClassBuilder.MethodBuilder<T> m) {
        m.addArgument(eventClassName, "event")
                .addArgument("Response", "response");
    }

    @Override
    protected <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void applyHeaders(String etagVarName, B bb) {
        bb.invoke("add").withArgumentFromField("ETAG").of("HttpHeaderNames")
                .withArgument(etagVarName).on("response");
        bb.invoke("add").withArgumentFromField("LAST_MODIFIED").of("HttpHeaderNames")
                .withArgumentFromInvoking("get").withArgument("path").on("timeStrings").on("response");
        bb.invoke("add").withArgumentFromField("CACHE_CONTROL").of("HttpHeaderNames")
                .withStringLiteral(cacheControl()).on("response");

    }

    @Override
    protected boolean shouldGenerateMatchMethods() {
        return false;
    }

    @Override
    protected <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void fetchIfNoneMatchHeader(String varName, B bb) {
        bb.declare(varName)
                .initializedByInvoking("header")
                .withArgumentFromField("IF_NONE_MATCH")
                .of("Headers")
                .on("event").as("String");
    }

    @Override
    protected <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void fetchIfModifiedSinceHeader(String varName, B bb) {
        bb.declare(varName)
                .initializedByInvoking("header")
                .withArgumentFromField("IF_MODIFIED_SINCE")
                .of("Headers")
                .on("event").as("CharSequence");
    }

}
