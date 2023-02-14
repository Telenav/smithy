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
package com.telenav.smithy.server.common;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.generators.SmithyGenerationSettings;
import java.util.UUID;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractRequestIdSupport {

    protected static final String BINDINGS_HOLDER_TYPE_NAME = "RequestIdTypes";
    protected static final String GUICE_BINDING_STATIC_VAR_NAME = "GUICE_BINDING_REQUEST_ID";
    public static final String SETTINGS_KEY_REQUEST_IDS_ENABLED = "useRequestIds";
    public static final String SETTINGS_KEY_ACCEPT_INBOUND_REQUEST_IDS = "allowInboundRequestIds";
    public static final String SETTINGS_KEY_FORWARD_CLIENT_REQUEST_IDS = "forwardClientRequestIds";
    public static final String SETTINGS_KEY_USE_UUID = "useUuidRequestIds";
    public static final String SETTINGS_KEY_REQUEST_ID_HEADER = "requestIdHeader";
    public static final String DEFAULT_REQUEST_ID_HEADER = "x-tn-rid";
    public static final String SETTINGS_KEY_CLIENT_REQUEST_ID_HEADER = "clientRequestIdHeader";
    public static final String DEFAULT_CLIENT_REQUEST_ID_HEADER = "x-tn-crid";
    protected final boolean enabled;
    protected final boolean allowInbound;
    protected final boolean useUuid;
    protected final boolean forward;
    protected final SmithyGenerationContext ctx;
    protected final String requestIdHeader;
    protected final String clientRequestIdHeader;

    protected AbstractRequestIdSupport(SmithyGenerationContext ctx) {
        this.ctx = notNull("ctx", ctx);
        SmithyGenerationSettings settings = ctx.settings();
        enabled = settings.getBoolean(SETTINGS_KEY_REQUEST_IDS_ENABLED).orElse(true);
        forward = settings.getBoolean(SETTINGS_KEY_FORWARD_CLIENT_REQUEST_IDS).orElse(false);
        allowInbound = settings.getBoolean(SETTINGS_KEY_ACCEPT_INBOUND_REQUEST_IDS).orElse(false);
        useUuid = settings.getBoolean(SETTINGS_KEY_USE_UUID).orElse(false);
        requestIdHeader = settings.getString(SETTINGS_KEY_REQUEST_ID_HEADER).orElse(DEFAULT_REQUEST_ID_HEADER);
        clientRequestIdHeader = settings.getString(SETTINGS_KEY_CLIENT_REQUEST_ID_HEADER).orElse(DEFAULT_CLIENT_REQUEST_ID_HEADER);
        if (forward && clientRequestIdHeader.equals(requestIdHeader)) {
            throw new Error("Client request id header and request id header have the same value: " + requestIdHeader);
        }
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

    public String requestIdVar() {
        return "requestId";
    }

    protected abstract String scopeBindTypeMethod();

    protected abstract String scopeTypeName();

    public abstract <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X>
            void generateRequestInjectionCode(ClassBuilder<?> cb, B bb, String eventVar, boolean hasOtherPrecursors);

    public abstract <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X>
            void createNullCheck(String varName, ClassBuilder<?> cb, B bb);

    public <T> void generateModuleMethods(ClassBuilder<T> cb) {
        if (!enabled) {
            return;
        }
        generateRequestIdBindingsHolderType(cb);

        cb.field("requestIds").ofType(BINDINGS_HOLDER_TYPE_NAME + "<?, ?>");

        cb.innerClass("GenericRequestIdFactoryLiteral", ib -> {
            ib.withModifier(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            cb.importing("com.google.inject.TypeLiteral");
            ib.extending("TypeLiteral<RequestIdFactory<?>>");
        });

        cb.method("withRequestIdFactory", mth -> {
            mth.withModifier(Modifier.PUBLIC).withTypeParam("ID");
            mth.addArgument("Class<? extends RequestIdFactory<ID>>", "type");
            mth.addArgument("Class<ID>", "ridType");
            mth.returning(cb.className());
            mth.body(bb -> {
                createNullCheck("type", cb, bb);
                createNullCheck("ridType", cb, bb);
                bb.assignField("requestIds").ofThis().toNewInstance()
                        .withArgument("ridType")
                        .withArgument("type")
                        .ofType(BINDINGS_HOLDER_TYPE_NAME + "<>");
                bb.returningThis();
            });
        });
    }

    public <T> void generateRequestIdBindingsHolderType(ClassBuilder<T> cb) {
        cb.importing("com.telenav.requestids.RequestIdFactory");
        cb.field(GUICE_BINDING_STATIC_VAR_NAME)
                .docComment("&#064;Named guice binding which can be used to obtain the current request id.")
                .withModifier(PUBLIC, STATIC, FINAL)
                .initializedWith("smithyRequestId");
        cb.innerClass(BINDINGS_HOLDER_TYPE_NAME, ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL);
            ib.withTypeParameters("T", "R extends RequestIdFactory<T>");

            ib.field("requestIdType").withModifier(FINAL)
                    .ofType("Class<T>");
            ib.field("requestIdFactoryType").withModifier(FINAL)
                    .ofType("Class<R>");

            ib.constructor(con -> {
                con.addArgument("Class<T>", "idType")
                        .addArgument("Class<R>", "factoryType");
                con.body(bb -> {
                    bb.assignField("requestIdType").ofThis().toExpression("idType");
                    bb.assignField("requestIdFactoryType").ofThis().toExpression("factoryType");
                });
            });

            ib.method("applyBindings", mth -> {
                mth.addArgument("Binder", "binder");
                mth.addArgument(scopeTypeName(), "scope");
                mth.body(bb -> {
                    bb.invoke(scopeBindTypeMethod())
                            .withArgument("binder")
                            .withArgument("requestIdType")
                            .on("scope");
                    bb.invoke("in").withArgument("SINGLETON")
                            .onInvocationOf("to")
                            .withArgument("requestIdFactoryType")
                            .onInvocationOf("bind")
                            .withNewInstanceArgument()
                            .ofType("GenericRequestIdFactoryLiteral")
                            .on("binder");

                    doDecorateApplyBindings(ib, bb, "binder", "scope");
                });
            });
        });
    }
    private void generateGenericRequestIdProviderBinding(ClassBuilder<?> cb) {
        cb.innerClass("RIDAsObjectProvider", ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL);
            ib.withTypeParameters("T", "R extends RequestIdFactory<T>");
            ib.implementing("Provider<Object>");

            ib.field("factoryProvider", fld -> {
                fld.withModifier(PRIVATE, FINAL)
                        .ofType("Provider<? extends RequestIdFactory<T>>");
            });
            ib.field("existingIdProvider", fld -> {
                fld.withModifier(PRIVATE, FINAL)
                        .ofType("Provider<T>");
            });
            ib.constructor(con -> {
                con.addArgument("Provider<? extends RequestIdFactory<T>>", "factoryProvider");
                con.addArgument("Provider<T>", "existingIdProvider");
                con.body(cbb -> {
                    cbb.assignField("factoryProvider").ofThis().toExpression("factoryProvider");
                    cbb.assignField("existingIdProvider").ofThis().toExpression("existingIdProvider");
                });
            });
            ib.overridePublic("get", mth -> {
                mth.returning("Object");
                mth.body(gbb -> {
                    gbb.declare("result").initializedByInvoking("get")
                            .on("existingIdProvider").as("Object");
                    gbb.ifNull("result").statement("result = factoryProvider.get().nextId()").endIf();
                    gbb.returning("result");
                });
            });
        });
    }


    protected <B extends BlockBuilderBase<T, B, X>, T, X>
            void doDecorateApplyBindings(ClassBuilder<?> cb, B bb, String binderVar, String scopeVar) {
        bb.declare("factoryProvider").initializedByInvoking("getProvider")
                .withArgument("requestIdFactoryType")
                .on(binderVar).as("Provider<R>");

        bb.declare("idProvider")
                .initializedByInvoking("getProvider")
                .withArgument("requestIdType")
                .on(binderVar)
                .as("Provider<T>");

        cb.importing("com.google.inject.name.Names");

        bb.invoke("toProvider")
                .withArgumentFromNew(nb -> {
                    nb.withArgument("factoryProvider")
                            .withArgument("idProvider")
                            .ofType("RIDAsObjectProvider<>");
                })
                .onInvocationOf("annotatedWith")
                .withArgumentFromInvoking("named")
                .withArgument(GUICE_BINDING_STATIC_VAR_NAME)
                .on("Names")
                .onInvocationOf("bind").withClassArgument("Object")
                .on(binderVar);
        decorateApplyBindings(cb, bb, binderVar, scopeVar);
    }

    protected <B extends BlockBuilderBase<T, B, X>, T, X>
            void decorateApplyBindings(ClassBuilder<?> cb, B bb, String binderVar, String scopeVar) {
        // do nothing - acteur support will

    }

    public <B extends BlockBuilderBase<T, B, X>, T, X>
            void generateBindingCode(ClassBuilder<?> cb, B bb, String binderVar, String scopeVar) {
        if (!enabled) {
            return;
        }
        generateGenericRequestIdProviderBinding(cb);
        IfBuilder<B> test = bb.ifNull("this.requestIds");
        test.lineComment("This will also prevent subsequent calls to set these from")
                .lineComment("succeeding, so the setter method does not need to check initialized state.");
        if (useUuid) {
            cb.importing(UUID.class);
            cb.importing("com.telenav.requestids.UUIDRequestIdFactory");
            test.assignField("requestIds").ofThis()
                    .toNewInstance(nb -> {
                        nb.withClassArgument("UUID")
                                .withClassArgument("UUIDRequestIdFactory")
                                .ofType(BINDINGS_HOLDER_TYPE_NAME + "<>");
                    });
        } else {
            cb.importing("com.telenav.requestids.DefaultRequestId");
            cb.importing("com.telenav.requestids.DefaultRequestIdFactory");
            test.assignField("requestIds").ofThis()
                    .toNewInstance(nb -> {
                        nb.withClassArgument("DefaultRequestId")
                                .withClassArgument("DefaultRequestIdFactory")
                                .ofType(BINDINGS_HOLDER_TYPE_NAME + "<>");
                    });

        }
        cb.importing("static com.google.inject.Scopes.SINGLETON");
        test.endIf();
        bb.invoke("applyBindings")
                .withArgument(binderVar).withArgument(scopeVar)
                .onField("requestIds").ofThis();
    }

}
