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

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ConstructorBuilder;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.util.UUID;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 *
 * @author Tim Boudreau
 */
public class RequestIdSupport {

    public static final String SETTINGS_KEY_REQUEST_IDS_ENABLED = "useRequestIds";
    public static final String SETTINGS_KEY_ACCEPT_INBOUND_REQUEST_IDS = "allowInboundRequestIds";
    public static final String SETTINGS_KEY_USE_UUID = "useUuidRequestIds";
    public static final String SETTINGS_KEY_REQUEST_ID_HEADER = "requestIdHeader";
    public static final String DEFAULT_REQUEST_ID_HEADER = "x-tn-rid";
    public static final String SETTINGS_KEY_CLIENT_REQUEST_ID_HEADER = "clientRequestIdHeader";
    public static final String DEFAULT_CLIENT_REQUEST_ID_HEADER = "x-tn-crid";

    private final boolean enabled;
    private final boolean allowInbound;
    private final boolean useUuid;
    private final SmithyGenerationContext ctx;
    private final String requestIdHeader;
    private final String clientRequestIdHeader;

    RequestIdSupport(SmithyGenerationContext ctx) {
        this.ctx = notNull("ctx", ctx);
        SmithyGenerationSettings settings = ctx.settings();
        enabled = settings.getBoolean(SETTINGS_KEY_REQUEST_IDS_ENABLED).orElse(true);
        allowInbound = settings.getBoolean(SETTINGS_KEY_ACCEPT_INBOUND_REQUEST_IDS).orElse(false);
        useUuid = settings.getBoolean(SETTINGS_KEY_USE_UUID).orElse(false);
        requestIdHeader = settings.getString(SETTINGS_KEY_REQUEST_ID_HEADER).orElse(DEFAULT_REQUEST_ID_HEADER);
        clientRequestIdHeader = settings.getString(SETTINGS_KEY_CLIENT_REQUEST_ID_HEADER).orElse(DEFAULT_CLIENT_REQUEST_ID_HEADER);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String requestIdHeader() {
        return requestIdHeader;
    }

    public String clientRequestIdHeader() {
        return clientRequestIdHeader;
    }

    <B extends BlockBuilderBase<T, B, X>, T, X> void decorateProbeHandlerConstructor(ClassBuilder<?> cb,
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
                        .addArgument("RequestIdFactory<T>", "factory")
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
        if (clientRequestIdHeader != null) {
            cb.field("CLIENT_ID_HEADER", fld -> {
                fld.initializedFromInvocationOf("cached")
                        .withStringLiteral(clientRequestIdHeader)
                        .on("AsciiString").withModifier(PRIVATE, STATIC, FINAL)
                        .ofType("CharSequence");
            });
        }
    }

    public String requestIdVar() {
        return "requestId";
    }

    <B extends BlockBuilderBase<T, B, X>, T, X> void generateRequestInjectionCode(ClassBuilder<?> cb, B bb,
            String eventVar) {
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

        if (clientRequestIdHeader != null) {
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

    public <T> void generateModuleMethods(ClassBuilder<T> cb) {
        if (!enabled) {
            return;
        }

        cb.importing("com.telenav.requestids.RequestIdFactory");
        cb.field("requestIdFactoryType").ofType("Class<? extends RequestIdFactory<?>>");
        cb.field("requestIdType").ofType("Class<?>");

        cb.innerClass("GenericRequestIdFactoryLiteral", ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL);
            cb.importing("com.google.inject.TypeLiteral");
            ib.extending("TypeLiteral<RequestIdFactory<?>>");
        });

        cb.method("withRequestIdFactory", mth -> {
            mth.withModifier(PUBLIC).withTypeParam("ID");
            mth.addArgument("Class<? extends RequestIdFactory<ID>>", "type");
            mth.addArgument("Class<ID>", "ridType");
            mth.returning(cb.className());
            mth.body(bb -> {
                validationExceptions().createNullCheck("type", cb, bb);
                validationExceptions().createNullCheck("ridType", cb, bb);
                bb.assignField("requestIdFactoryType").ofThis().toExpression("type");
                bb.assignField("requestIdType").ofThis().toExpression("ridType");
                bb.returningThis();
            });
        });
    }

    <B extends BlockBuilderBase<T, B, X>, T, X> void generateBindingCode(ClassBuilder<?> cb, B bb,
            String binderVar, String scopeVar) {
        if (!enabled) {
            return;
        }
        ClassBuilder.IfBuilder<B> test = bb.ifNull("requestIdFactoryType");
        test.lineComment("This will also prevent subsequent calls to set these from")
                .lineComment("succeeding, so the setter method does not need to check initialized state.");
        if (useUuid) {
            cb.importing(UUID.class);
            cb.importing("com.telenav.requestids.UUIDRequestIdFactory");
            test.assignField("requestIdFactoryType").ofThis().toExpression("UUIDRequestIdFactory.class");
            test.assignField("requestIdType").ofThis().toExpression("UUID.class");
        } else {
            cb.importing("com.telenav.requestids.DefaultRequestId");
            cb.importing("com.telenav.requestids.DefaultRequestIdFactory");
            test.assignField("requestIdFactoryType").ofThis().toExpression("DefaultRequestIdFactory.class");
            test.assignField("requestIdType").ofThis().toExpression("DefaultRequestId.class");
        }
        cb.importing("static com.google.inject.Scopes.SINGLETON");

        test.endIf();
        bb.invoke("bindType").withArgument(binderVar).withArgument("requestIdType").on(scopeVar);
        bb.invoke("in").withArgument("SINGLETON")
                .onInvocationOf("to").withArgument("requestIdFactoryType")
                .onInvocationOf("bind").withNewInstanceArgument().ofType("GenericRequestIdFactoryLiteral")
                .on("binder");
    }

}
