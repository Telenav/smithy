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
package com.telenav.smithy.vertx.server.generator;

import com.telenav.smithy.server.common.AbstractRequestIdSupport;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import com.telenav.smithy.generators.SmithyGenerationContext;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 *
 * @author Tim Boudreau
 */
public class RequestIdSupport extends AbstractRequestIdSupport {

    RequestIdSupport(SmithyGenerationContext ctx) {
        super(ctx);
    }

    @Override
    public <B extends BlockBuilderBase<T, B, X>, T, X> void createNullCheck(String varName, ClassBuilder<?> cb, B bb) {
        validationExceptions().createNullCheck(varName, cb, bb);
    }

    protected <B extends BlockBuilderBase<T, B, X>, T, X> void decorateProbeHandlerConstructor(ClassBuilder<?> cb,
            ConstructorBuilder<?> con, B bb) {
        if (!enabled) {
            return;
        }
        cb.importing("com.telenav.requestids.RequestIdFactory");
        cb.field("requestIdFactory").withModifier(PRIVATE, FINAL)
                .ofType("RequestIdFactory<?>");
        con.addArgument("RequestIdFactory<?>", "requestIds");
        bb.assignField("requestIdFactory").ofThis().toExpression("requestIds");

        if (allowInbound) {
            cb.method("extractOrCreateRequestId", mth -> {
                mth.withModifier(PRIVATE, STATIC, FINAL);
                mth.withTypeParam("ID")
                        .addArgument("RequestIdFactory<ID>", "factory")
                        .addArgument("RoutingContext", "event")
                        .returning("ID");
                mth.body(mb -> {
                    mb.returningInvocationOf("orElseGet")
                            .withMethodReference("nextId").on("factory")
                            .onInvocationOf("fromString")
                            .withArgumentFromInvoking("getHeader")
                            .withArgument("REQUEST_ID_HEADER")
                            .onInvocationOf("request")
                            .on("event")
                            .on("factory");
                });
            });
        }
        cb.importing("io.netty.util.AsciiString");
        cb.field("REQUEST_ID_HEADER", fld -> {
            fld.initializedFromInvocationOf("cached")
                    .withStringLiteral(requestIdHeader)
                    .on("AsciiString").withModifier(PRIVATE, STATIC, FINAL)
                    .ofType("CharSequence");
        });
        if (forward) {
            cb.field("CLIENT_ID_HEADER", fld -> {
                fld.initializedFromInvocationOf("cached")
                        .withStringLiteral(clientRequestIdHeader)
                        .on("AsciiString").withModifier(PRIVATE, STATIC, FINAL)
                        .ofType("CharSequence");
            });
        }
    }

    @Override
    protected String scopeTypeName() {
        return "RequestScope";
    }

    public <B extends BlockBuilderBase<T, B, X>, T, X> void generateRequestInjectionCode(ClassBuilder<?> cb, B bb, String eventVar, boolean hasOtherPrecursors) {
        if (!enabled) {
            return;
        }
        if (allowInbound) {
            bb.declare("requestId").initializedByInvoking("extractOrCreateRequestId")
                    .withArgument("requestIdFactory")
                    .withArgument(eventVar)
                    .inScope().as("Object");
        } else {
            bb.declare("requestId").initializedByInvoking("nextId")
                    .on("requestIdFactory").as("Object");
        }
        cb.importing("com.telenav.smithy.vertx.probe.Probe");
        bb.invoke("put").withArgument("Probe.REQUEST_ID_KEY")
                .withArgument("requestId")
                .on(eventVar);

        //         event.response().putHeader(REQUEST_ID_HEADER, requestId.toString());
        bb.invoke("putHeader").withArgument("REQUEST_ID_HEADER")
                .withArgumentFromInvoking("toString").on("requestId")
                .onInvocationOf("response")
                .on(eventVar);

        if (forward) {
            bb.declare("clid")
                    .initializedByInvoking("getHeader")
                    .withArgument("CLIENT_ID_HEADER")
                    .onInvocationOf("request")
                    .on(eventVar)
                    .as("String");
            bb.ifNotNull("clid")
                    .invoke("putHeader")
                    .withArgument("CLIENT_ID_HEADER")
                    .withArgument("clid")
                    .onInvocationOf("response")
                    .on(eventVar).endIf();
        }
    }

    @Override
    protected String scopeBindTypeMethod() {
        return "bindType";
    }
}
