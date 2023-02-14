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
package com.telenav.smithy.simple.server.generator;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.generators.SmithyGenerationContext;
import com.telenav.smithy.server.common.AbstractRequestIdSupport;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.util.function.Consumer;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 *
 * @author Tim Boudreau
 */
final class ActeurRequestIdSupport extends AbstractRequestIdSupport {
    private static final String ON_BEFORE_IMPL_NAME = "RequestIdInjector";
    private final Consumer<ClassBuilder<String>> cconsumer;

    ActeurRequestIdSupport(SmithyGenerationContext ctx, Consumer<ClassBuilder<String>> cconsumer) {
        super(ctx);
        this.cconsumer = cconsumer;
    }

    @Override
    public <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X> void generateBindingCode(ClassBuilder<?> cb,
            B bb, String binderVar, String scopeVar) {
        if (!enabled) {
            return;
        }
        super.generateBindingCode(cb, bb, binderVar, scopeVar);
        cb.importing("static com.google.inject.Scopes.SINGLETON", "com.mastfrog.acteur.ResponseDecorator",
                "javax.inject.Provider", "com.google.inject.name.Named");
        bb.invoke("in").withArgument("SINGLETON")
                .onInvocationOf("to")
                .withClassArgument("RequestIdResponseDecorator")
                .onInvocationOf("bind")
                .withClassArgument("ResponseDecorator")
                .on(binderVar);

        bb.invoke("to").withClassArgument(ON_BEFORE_IMPL_NAME)
                .onInvocationOf("bind").withClassArgument("OnBeforeEvent")
                .on(binderVar);
        generateResponseDecorator(cb);
        generateIdInjector(cb);
    }

    private void generateResponseDecorator(ClassBuilder<?> cb) {
        cb.innerClass("RequestIdResponseDecorator", ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL)
                    .implementing("ResponseDecorator");

            ib.field("REQUEST_ID_HEADER", fld -> {
                fld.initializedFromInvocationOf("header")
                        .withArgumentFromInvoking("cached")
                        .withStringLiteral(requestIdHeader())
                        .on("AsciiString")
                        .inScope()
                        .withModifier(PRIVATE, STATIC, FINAL)
                        .ofType("HeaderValueType<CharSequence>");
            });

            if (forward) {
                cb.field("CLIENT_REQUEST_ID_HEADER", fld -> {
                    fld.initializedFromInvocationOf("header")
                            .withArgumentFromInvoking("cached")
                            .withStringLiteral(clientRequestIdHeader())
                            .on("AsciiString")
                            .inScope()
                            .withModifier(PRIVATE, STATIC, FINAL)
                            .ofType("HeaderValueType<CharSequence>");
                });

            }

            ib.field("reqIds").withModifier(PRIVATE, FINAL)
                    .ofType("Provider<Object>");

            ib.constructor(con -> {
                con.annotatedWith("Inject").closeAnnotation();
                con.addMultiAnnotatedArgument("Named")
                        .addExpressionArgument("value", GUICE_BINDING_STATIC_VAR_NAME)
                        .closeAnnotation().closeAnnotations().named("reqIds").ofType("Provider<Object>");
                con.body("this.reqIds = reqIds");
            });
            cb.importing("com.mastfrog.acteur.Application",
                    "io.netty.handler.codec.http.HttpResponseStatus",
                    "com.mastfrog.acteur.Event", "com.mastfrog.acteur.HttpEvent",
                    "com.mastfrog.acteur.Acteur", "com.mastfrog.acteur.Page",
                    "com.mastfrog.acteur.Response", "com.mastfrog.acteur.headers.Headers");
            ib.overridePublic("onBeforeSendResponse", mth -> {
                mth.addArgument("Application", "application");
                mth.addArgument("HttpResponseStatus", "status");
                mth.addArgument("Event<?>", "event");
                mth.addArgument("Response", "response");
                mth.addArgument("Acteur", "acteur");
                mth.addArgument("Page", "page");
                mth.body(mbb -> {
                    mbb.declare("idH")
                            .initializedByInvoking("toString")
                            .onInvocationOf("get")
                            .on("reqIds")
                            .as("String");
                    mbb.invoke("add")
                            .withArgument("REQUEST_ID_HEADER")
                            .withArgument("idH")
                            .on("response");
                    mbb.blankLine().lineComment("It always will be HttpEvent unless the server also includes")
                            .lineComment("websocket calls.");

                    if (super.forward) {
                        IfBuilder<?> test = mbb.iff(variable("event").isInstance("HttpEvent"));
                        test.declare("http").initializedTo().castTo("HttpEvent")
                                .expression("event").as("HttpEvent");

                        test.invoke("ifPresent")
                                .withLambdaArgument().withArgument("cid")
                                .body(lbb -> {
                                    lbb.invoke("add")
                                            .withArgument("CLIENT_REQUEST_ID_HEADER")
                                            .withArgument("cid")
                                            .on("response");
                                }).onInvocationOf("httpHeader").withStringLiteral(clientRequestIdHeader())
                                .on("http");

                        test.endIf();
                    }
                });
            });
        });
    }

    @Override
    protected <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X> void decorateApplyBindings(
            ClassBuilder<?> cb, B bb, String binderVar, String scopeVar) {

        cb.importing("com.mastfrog.acteur.ResponseDecorator");
        
        bb.invoke("in").withArgument("SINGLETON")
                .onInvocationOf("to")
                .withClassArgument("RequestIdResponseDecorator")
                .onInvocationOf("bind")
                .withClassArgument("ResponseDecorator")
                .on(binderVar);
    }

    @Override
    protected String scopeTypeName() {
        return "ReentrantScope";
    }

    @Override
    public <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X>
            void generateRequestInjectionCode(ClassBuilder<?> cb, B bb, String eventVar, boolean hasOtherPrecursors) {
        // do nothing - it is handled by the bindings we set up
    }

    @Override
    public <B extends ClassBuilder.BlockBuilderBase<T, B, X>, T, X>
            void createNullCheck(String varName, ClassBuilder<?> cb, B bb) {
        validationExceptions().createNullCheck(varName, cb, bb);
    }

    @Override
    protected String scopeBindTypeMethod() {
        return "bindTypesAllowingNulls";
    }

    private <T> void generateIdInjector(ClassBuilder<T> outer) {
        ClassBuilder<ClassBuilder<T>> cb = outer.innerClass(ON_BEFORE_IMPL_NAME)
                .importing("com.mastfrog.acteur.Acteur", "javax.inject.Inject",
                        "com.telenav.requestids.RequestIdFactory",
                        "io.netty.util.AsciiString", "com.mastfrog.acteur.headers.HeaderValueType",
                        "static com.mastfrog.acteur.headers.Headers.header")
                .withModifier(PRIVATE, STATIC, FINAL)
                .implementing("OnBeforeEvent");
        cb.docComment("Generates or decodes a request ID and makes it available in the request scope and "
                + "HTTP headers of the response.");
        cb.field("REQUEST_ID_HEADER", fld -> {
            fld.initializedFromInvocationOf("header")
                    .withArgumentFromInvoking("cached")
                    .withStringLiteral(requestIdHeader())
                    .on("AsciiString")
                    .inScope()
                    .withModifier(PRIVATE, STATIC, FINAL)
                    .ofType("HeaderValueType<CharSequence>");
        });
        cb.field("factory").withModifier(FINAL, PRIVATE).ofType("RequestIdFactory<?>");

        cb.field("ARR")
                .withModifier(PRIVATE, STATIC, FINAL)
                .initializedFromInvocationOf("withInitial")
                .withLambdaArgument(lb -> {
                    lb.body().returning("new Object[2]").endBlock();
                }).on("ThreadLocal").ofType("ThreadLocal");

        cb.constructor(con -> {
            con.annotatedWith("Inject").closeAnnotation();
            con.addArgument("RequestIdFactory<?>", "factory");
            con.body(bb -> {
                bb.assignField("factory").ofThis().toExpression("factory");
            });
        });
        cb.overridePublic("onBeforeEvent", mm -> {
            mm.returning("Object[]");
            cb.importing("com.mastfrog.acteur.OnBeforeEvent");
            cb.importing("io.netty.channel.Channel");
            cb.importing("com.mastfrog.acteur.Event");

            mm.addArgument("Event<?>", "event")
                    .addArgument("Channel", "channel")
                    .addArgument("Object", "internalId");
            mm.body(bb -> {

                bb.statement("Object[] arr = (Object[]) ARR.get()");
                bb.statement("arr[0] = internalId");
                if (!super.allowInbound) {
                    bb.lineComment("Generated with allowInboundRequestIds set to false - do not")
                            .lineComment("investigate the inbound request for a request id to echo.");
                    bb.declare("id")
                            .initializedByInvoking("nextId").on("factory").as("Object");

                } else {
                    bb.blankLine().lineComment("Generated with allowInboundRequestIds set to TRUE - if a header")
                            .lineComment("named " + requestIdHeader() + " exists in the inbound request,")
                            .lineComment("and it can be parsed, use it.");

                    bb.declare("id").as("Object");
                    bb.lineComment("Unless we have a websocket request, it will be HttpEvent.");
                    bb.iff(variable("event").isInstance("HttpEvent"))
                            .assign("id").toInvocation("idFrom")
                            .withArgument("factory")
                            .withArgument("(HttpEvent) event")
                            .inScope().orElse()
                            .assign("id").toInvocation("nextId").on("factory").endIf();

                    cb.method("idFrom", mth -> {
                        mth.withModifier(PRIVATE)
                                .docComment("Finds an inbound header matching " + requestIdHeader() + " in the "
                                        + "inbound request, and decodes and uses it if present and decodeable, and "
                                        + "generates a new request id if not."
                                        + "\n@param factory The request id factory"
                                        + "\n@param evt The http request"
                                        + "\n@return An object of whatever type the bound RequestIdFactory uses to generate ids")
                                .returning("Object")
                                .withTypeParam("T")
                                .addArgument("RequestIdFactory<T>", "factory")
                                .addArgument("HttpEvent", "evt");
                        mth.body(mbb -> {
                            mbb.returningInvocationOf("orElseGet")
                                    .withLambdaArgument().body().returningInvocationOf("nextId").on("factory").endBlock()
                                    .onInvocationOf("flatMap")
                                    .withLambdaArgument()
                                    .withArgument("hdrVal")
                                    .body().returningInvocationOf("fromString").withArgument("hdrVal").on("factory").endBlock()
                                    .onInvocationOf("httpHeader")
                                    .withArgument("REQUEST_ID_HEADER")
                                    .on("evt");
                        });
                    });
                }
                bb.statement("arr[1] = id");
                bb.returning("arr");
            });
        });
        cb.build();
    }

}
