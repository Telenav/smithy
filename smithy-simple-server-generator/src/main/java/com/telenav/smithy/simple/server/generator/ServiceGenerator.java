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
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Value;
import com.mastfrog.java.vogon.ClassBuilder.Variable;
import static com.mastfrog.java.vogon.ClassBuilder.invocationOf;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.generators.PostGenerateTask;
import com.telenav.smithy.generators.SmithyGenerationContext;
import static com.telenav.smithy.generators.SmithyGenerationContext.MARKUP_PATH_CATEGORY;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.telenav.smithy.java.generators.builtin.struct.impl.Registry.applyGeneratedAnnotation;
import com.telenav.smithy.extensions.AuthenticatedTrait;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.names.TypeNames.typeNameOf;
import static com.telenav.smithy.names.operation.OperationNames.authPackage;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceFqn;
import static com.telenav.smithy.names.operation.OperationNames.operationInterfaceName;
import com.telenav.smithy.server.common.OperationEnumBindingGenerator;
import static com.telenav.smithy.simple.server.generator.OperationGenerator.ensureGraphs;
import com.telenav.smithy.utils.ResourceGraph;
import static com.telenav.smithy.utils.ResourceGraphs.graph;
import static com.telenav.smithy.utils.ShapeUtils.maybeImport;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import static java.lang.Character.isUpperCase;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.element.Modifier.VOLATILE;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import static software.amazon.smithy.model.shapes.ShapeType.OPERATION;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
final class ServiceGenerator extends AbstractJavaGenerator<ServiceShape> {

    private ActeurRequestIdSupport requestIdSupport;
    private OperationEnumBindingGenerator operationEnumSupport;

    ServiceGenerator(ServiceShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        requestIdSupport = new ActeurRequestIdSupport(ctx, addTo);
        operationEnumSupport = new OperationEnumBindingGenerator(shape, addTo, names());
        ClassBuilder<String> cb = ClassBuilder.forPackage(names().packageOf(shape))
                .named(typeNameOf(shape))
                .withModifier(FINAL, PUBLIC)
                .implementing("Module")
                .importing("com.google.inject.Module");

        applyDocumentation(cb);
        applyGeneratedAnnotation(ServiceGenerator.class, cb);

        ResourceGraph graph = graph(model, shape);
        Set<OperationShape> operations = new LinkedHashSet<>();
        graph.closure(shape)
                .forEach(shape -> shape.asOperationShape().ifPresent(operations::add));
        createStartMethod(cb);
        createMainMethod(cb);
        createConfigureMethod(cb, operations, graph);
        createSettingsMethodAndField(cb);
        createScopeFieldAndGetter(cb);
        createInitializedCheck(cb);
        createAdditionalModulesMethodAndSet(cb);
        createExceptionEvaluationExtensions(cb);
        requestIdSupport.generateModuleMethods(cb);

        operations.forEach(op -> addBindingFieldAndMethod(cb, op));
        addBindingFieldsAndMethodsForAuthenticators(operations, cb);

        createExceptionEvaluatorImplementations(cb);
        registerZipMarkupTask();

        addTo.accept(cb);
    }

    private void registerZipMarkupTask() {
        SmithyGenerationContext context = ctx();
        String packagePath = names().packageOf(shape).replace('.', '/');
        String markupRelativePath = context.settings().getString("simple-server-markup-src-relative-path").orElse("../resources/" + packagePath + "/markup.zip");
        ctx().session().registerPostGenerationTask(getClass().getName() + "-zip-markup",
                () -> PostGenerateTask.zipCategory(MARKUP_PATH_CATEGORY, destSourceRoot.resolve(markupRelativePath).toAbsolutePath()));
    }

    @Override
    protected String additionalDocumentation() {
        StringBuilder sb = new StringBuilder("Guice module for setting up and running a server for "
                + shape.getId().getName() + ".  Has a straightforward main() method "
                + "for running the server with no service interface implementations installed, "
                + "which is useful for viewing generated API docs and ensuring the web API "
                + "looks correct."
                + "\nCan be used either via the start method, or as a plain Guice module along "
                + "with others, launched by your own code (use the generated start() method as "
                + "a guide for how to do that).");
        sb.append("\n<h2>Resources and Operations</h2>\n<ul>");
        ResourceGraph graph = SmithyServerGenerator.maybeBuildGraph(shape, model);
        graph.closure(shape).forEach(child
                -> child.asResourceShape().ifPresent(resource -> {
                    String friendlyName = deBicapitalize(resource.getId().getName());
                    sb.append("<li><b>").append(friendlyName).append("</b> - <code>")
                            .append(resource.getId()).append("</code>");
                    resource.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                        sb.append(" - ").append(dox.getValue());
                    });
                    sb.append("<ul>");
                    graph.closure(child).forEach(ch -> {
                        ch.asOperationShape().ifPresent(op -> {
                            sb.append("<li><b><i>")
                                    .append(op.getId().getName())
                                    .append("</i></b> - <code>")
                                    .append(op.getId())
                                    .append("</code>");

                            op.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                                sb.append(" - ").append(dox.getValue());
                            });
                            String opName = operationInterfaceName(op);
                            sb.append("<ul><li><code>").append(opName)
                                    .append("</code> - implement {@link ")
                                    .append(opName).append("} and call <code>with")
                                    .append(opName).append("Type(My").append(opName).append(".class)")
                                    .append("</code> to bind your implementation before "
                                            + "starting the server to respond to requests "
                                            + "for this operation.</ul></li>");
                            sb.append("</li>");
                        });
                    });
                    sb.append("</ul>");
                    sb.append("</li>");
                }));
        return sb.append("</ul>").toString();
    }

    private String deBicapitalize(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean lastWasCaps = true;
        for (char c : s.toCharArray()) {
            if (isUpperCase(c)) {
                if (!lastWasCaps) {
                    sb.append(' ');
                }
                lastWasCaps = true;
            } else {
                lastWasCaps = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private void addBindingFieldAndMethod(ClassBuilder<String> cb, OperationShape shape) {
        String ifaceFqn = operationInterfaceFqn(model, shape);
        String ifaceName = operationInterfaceName(shape);
        String fieldName = decapitalize(ifaceName) + "Type";
        String[] fqns = new String[]{ifaceFqn};
        maybeImport(cb, fqns);
        cb.field(fieldName, fld
                -> fld.withModifier(PRIVATE).ofType("Class<? extends " + ifaceName + ">"));
        cb.method("with" + ifaceName + "Type", mth -> {
            mth.docComment("Provide an implementation of {@link " + ifaceName + "} to bind for "
                    + "responding to requests that invoke the <code>" + shape.getId() + "</code> "
                    + "operation. If the server is started without binding anything for this type, "
                    + "then a default implementation that simply returns an HTTP <code>501 Unimplemented</code> "
                    + "response will be used."
                    + "\n@param type - The implementation of " + ifaceName
                    + "\n@return this"
                    + "\n@throws IllegalArgumentException if the passed type is null or is not assignable to " + ifaceName
                    + ", or if the passed type is abstract."
                    + "\n@throws IllegalStateException if the " + ifaceName + " has already been "
                    + "set, or if the Guice injector has already been initialized (server has been started)."
            );
            mth.withModifier(PUBLIC)
                    .addArgument("Class<? extends " + ifaceName + ">", "type")
                    .returning(cb.className())
                    .body(bb -> {
                        generateTypeFieldAssignment(bb, ifaceName, cb, fieldName);
                    });
        });

    }

    private void generateTypeFieldAssignment(ClassBuilder.BlockBuilder<?> bb, String ifaceName, ClassBuilder<String> cb, String fieldName) {
        bb.ifNull("type")
                .andThrow(nb -> {
                    nb.withStringLiteral("Type for " + ifaceName
                            + " may not be null.")
                            .ofType("IllegalArgumentException");
                }).endIf();
        bb.iff().booleanExpression("type == " + ifaceName + ".class")
                .andThrow(nb -> {
                    nb.withStringLiteral("Cannot bind to the interface type")
                            .ofType("IllegalArgumentException");
                }).endIf();

        cb.importing("static java.lang.reflect.Modifier.ABSTRACT");
        bb.iff().booleanExpression("(type.getModifiers() & ABSTRACT) != 0")
                .andThrow(nb -> {
                    nb.withStringConcatentationArgument("Cannot bind an "
                            + "abstract class or interface: ")
                            .appendExpression("type")
                            .endConcatenation().ofType("IllegalArgumentException");
                }).endIf();

        bb.ifNotNull("this." + fieldName)
                .andThrow(nb -> {
                    nb.withStringConcatentationArgument(fieldName + " is ")
                            .append("already set to ").appendExpression("this." + fieldName)
                            .endConcatenation()
                            .ofType("IllegalStateException");
                }).endIf();
        bb.iff().booleanExpression("!" + ifaceName + ".class.isAssignableFrom(type)")
                .andThrow(nb -> {
                    nb.withStringConcatentationArgument("Not actually ")
                            .append("an implementation of " + ifaceName + ": ")
                            .appendExpression("type")
                            .endConcatenation()
                            .ofType("IllegalArgumentException");
                }).endIf();

        bb.invoke("checkInitialized").inScope();
        bb.statement("this." + fieldName + " = type");
        bb.returningThis();
    }

    private void createConfigureMethod(ClassBuilder<String> cb, Set<OperationShape> operations, ResourceGraph graph) {
        // // public void configure(Binder binder) {
        cb.overridePublic("configure", mth -> {
            cb.importing("com.google.inject.Binder");
            mth.addArgument("Binder", "binder")
                    .body(bb -> {
                        bb.lineComment("Bind any types passed to setters to configure")
                                .lineComment("SPI implementation bindings");
                        operations.forEach(op -> {
                            String ifaceName = operationInterfaceName(op);
                            String fieldName = decapitalize(ifaceName) + "Type";
                            bb.ifNotNull(fieldName)
                                    .invoke("to")
                                    .withArgumentFromField(fieldName).ofThis()
                                    .onInvocationOf("bind")
                                    .withClassArgument(ifaceName)
                                    .on("binder")
                                    .endIf();
                        });

                        bb.blankLine().lineComment("Set up a binding for any exception evaluators passed")
                                .lineComment("to methods here.");
                        bb.invoke("toInstance")
                                .withArgument(decapitalize(exceptionEvaluatorListTypeName()))
                                .onInvocationOf("bind")
                                .withClassArgument(exceptionEvaluatorListTypeName())
                                .on("binder");

                        bb.blankLine().lineComment("Handles thrown exceptions and maps them to error responses.")
                                .lineComment("Binding it as an eager singleton registers it on startup.");
                        bb.invoke("asEagerSingleton")
                                .onInvocationOf("bind")
                                .withClassArgument("ExceptionEvaluatorImpl")
                                .on("binder");

                        bb.blankLine().lineComment("Install the acteur bindings for SmithyRequest and SmithyResponse.");
                        cb.importing("com.telenav.smithy.acteur.adapter.SmithyActeurAdapter");
                        bb.invoke("install")
                                .withArgumentFromNew(nb -> nb.ofType("SmithyActeurAdapter"))
                                .on("binder");

                        generateAuthBindings(cb, bb, operations, "binder");

                        bb.blankLine().lineComment("Install any modules passed to methods here.");
                        bb.invoke("forEach")
                                .withMethodReference("install").on("binder")
                                .onField("modules").ofThis();

                        requestIdSupport.generateBindingCode(cb, bb, "binder", "scope");

                        bb.invoke("bindTypesAllowingNulls")
                                .withArgument("binder")
                                .withClassArgument(operationEnumSupport.operationEnumTypeName())
                                .on("scope");

                        operationEnumSupport.generateEnumBinding(cb, bb, "binder");

                        bb.blankLine().lineComment("Set the initialized flag so that attempts to")
                                .lineComment("set up bindings after server start will throw, since")
                                .lineComment("they would not bind anything at that point.");
                        bb.statement("initialized = true");
                    });
        });
    }

    private void createExceptionEvaluatorImplementations(ClassBuilder<String> cb) {
        cb.importing("com.mastfrog.acteur.errors.ErrorResponse",
                "com.mastfrog.acteur.errors.ExceptionEvaluator",
                "com.mastfrog.acteur.errors.ExceptionEvaluatorRegistry",
                "io.netty.handler.codec.http.HttpResponseStatus",
                "javax.inject.Inject",
                "com.mastfrog.acteur.Acteur",
                "com.mastfrog.acteur.Page",
                "com.mastfrog.acteur.Event",
                validationExceptions().fqn()
        ).innerClass("ExceptionEvaluatorImpl", ib -> {
            ib.withModifier(PRIVATE, STATIC, FINAL)
                    .extending("ExceptionEvaluator");
            ib.docComment("Interprets exceptions thrown during the request cycle into "
                    + "an appropriate HTTP response code and error message body.");
            ib.constructor(con -> {
                con.annotatedWith("Inject").closeAnnotation()
                        .addArgument("ExceptionEvaluatorRegistry", "registry")
                        .addArgument(exceptionEvaluatorListTypeName(), "customEvaluators")
                        .body(bb -> {
                            bb.invoke("super").withArgument("registry").inScope();
                            bb.statement("this.customEvaluators = customEvaluators");
                        });
            });
            ib.field("customEvaluators", fld -> {
                fld.withModifier(PRIVATE, FINAL)
                        .ofType(exceptionEvaluatorListTypeName());
            });
            ib.overridePublic("evaluate", mth -> {
                mth.docComment("Is passed any thrown exception for a chance to craft the "
                        + "response."
                        + "\n@param thrown The exception"
                        + "\n@param thrower The Acteur within whose scope the exception was thrown"
                        + "\n@param thrownIn The response chain the Acteur was instantiated for"
                        + "\n@param inResponseTo The request (HttpEvent or WebSocketEvent)"
                        + "\n@return An error response, or null if the exception is unrecognized "
                        + "(in which case any other evaluators installed in the application will "
                        + "get a crack at it)");
                mth.addArgument("Throwable", "thrown")
                        .addArgument("Acteur", "thrower")
                        .addArgument("Page", "thrownIn")
                        .addArgument("Event<?>", "inResponseTo")
                        .returning("ErrorResponse")
                        .body(bb -> {
                            bb.lineComment("First try any custom evaluators bound during startup:");
                            bb.simpleLoop("Function<? super Throwable, ? extends Optional<ErrorResponse>>", "f")
                                    .over("customEvaluators", loop -> {
                                        loop.declare("result")
                                                .initializedByInvoking("apply")
                                                .withArgument("thrown")
                                                .on("f")
                                                .as("Optional<ErrorResponse>");
                                        loop.iff(invocationOf("isPresent").on("result"))
                                                .returningInvocationOf("get").on("result")
                                                .endIf();
                                    });
                            bb.blankLine().lineComment("Exception is the built-in type for validation failure.");
                            bb.iff(variable("thrown").isInstance(validationExceptions().name()))
                                    .returningInvocationOf("badRequest")
                                    .withStringConcatentationArgument("Invalid input: ")
                                    .appendInvocationOf("getMessage").on("thrown")
                                    .endConcatenation()
                                    .on("Err")
                                    .endIf();

                            cb.importing("com.telenav.smithy.http.ResponseException", "com.mastfrog.acteur.errors.Err");

                            bb.blankLine().lineComment("Exception is the built-in generic status code + error message exception.");
                            ClassBuilder.IfBuilder<?> reTest = bb.iff(variable("thrown").isInstance("ResponseException"));
                            reTest.declare("re")
                                    .initializedTo().castTo("ResponseException").expression("thrown").as("ResponseException");
                            reTest.declare("result")
                                    .initializedByInvoking("withCode")
                                    .withArgumentFromInvoking("status").on("re")
                                    .withArgumentFromInvoking("getMessage").on("re")
                                    .on("Err")
                                    .as("Err");
                            reTest.blankLine().lineComment("ResponseException can propagate the WWW-Authenticate header, which is")
                                    .lineComment("critical for authentication to work properly.");
                            reTest.invoke("withHeaderNameAndValue")
                                    .withLambdaArgument(lam -> {
                                        lam.withArgument("headerName")
                                                .withArgument("headerValue");
                                        lam.body().invoke("withHeader")
                                                .withArgument("headerName")
                                                .withArgument("headerValue")
                                                .on("result")
                                                .endBlock();
                                    })
                                    .on("re");
                            reTest.returning("result").endIf();

                            bb.blankLine().lineComment("UnsupportedOperationException is thrown by the mock implementations")
                                    .lineComment("of the generated service interfaces, and is appropriately translated")
                                    .lineComment("as the 501 Not Implemented HTTP response code");

                            bb.iff(variable("thrown").isInstance("UnsupportedOperationException"))
                                    .returningInvocationOf("create")
                                    .withArgumentFromField("NOT_IMPLEMENTED").of("HttpResponseStatus")
                                    .withStringConcatentationArgument("Unimplemented: ")
                                    .appendInvocationOf("getMessage").on("thrown")
                                    .endConcatenation()
                                    .on("ErrorResponse")
                                    .endIf();
                            bb.blankLine().lineComment("Unrecognized - let the framework's default error reporting take over.");
                            bb.returningNull();
                        });
            });
        });
    }

    private void createStartMethod(ClassBuilder<String> cb) {
        cb.method("start", mb -> {
            mb.docComment("Starts the server.\n@param args Any command-line arguments\n"
                    + "@return A ServerControl - call await() on it to block until server shutdown\n"
                    + "@throws Exception if something goes wrong starting the server (port in use, "
                    + "mis-configuration or similar)");
            cb.importing("com.mastfrog.acteur.util.ServerControl");
            mb.throwing("Exception")
                    .withModifier(PUBLIC)
                    .addArgument("String...", "args")
                    .returning("ServerControl")
                    .body(bb -> {
                        cb.importing("com.mastfrog.settings.SettingsBuilder");
                        cb.importing("com.mastfrog.settings.Settings");

                        bb.lineComment("Configure our settings - port and similar");
                        bb.declare("settingsBuilder")
                                .initializedByInvoking("addDefaultsFromEtc")
                                .onInvocationOf("addDefaultsFromProcessWorkingDir")
                                .onInvocationOf("builder")
                                .withStringLiteral(shape.getId().getName())
                                .on("Settings")
                                .as("SettingsBuilder");

                        bb.lineComment("Let the consumer configure any default values")
                                .lineComment("(common args are defined on ServerModule)");
                        bb.invoke("accept").withArgument("settingsBuilder")
                                .on("settingsConsumer");

                        bb.lineComment("Let the command-line arguments override any default values");
                        bb.declare("settings").initializedByInvoking("build")
                                .onInvocationOf("parseCommandLineArguments")
                                .withArgument("args")
                                .on("settingsBuilder").as("Settings");

                        bb.lineComment("Now build our server");
                        cb.importing("com.mastfrog.acteur.server.ServerBuilder");
                        bb.declare("bldr").initializedWithNew(nb -> {
                            nb.withArgument("scope")
                                    .ofType("ServerBuilder");
                        }).as("ServerBuilder");

//                        if (!ifaceTypeForField.isEmpty()) {
//                            cb.importing(SmithyExtensionsModule.class);
//                            bb.lineComment("We have some cache header shortcut implementations, so we need");
//                            bb.lineComment("the SmithyExtensionsModule which registers bindings needed by them.");
//                            bb.invoke("add")
//                                    .withArgumentFromNew(nb
//                                            -> nb.withArgumentFromInvoking("scope")
//                                            .on("bldr")
//                                            .ofType("SmithyExtensionsModule"))
//                                    .on("bldr");
//                        }
                        bb.lineComment("Enables built-in API help - once started, see")
                                .lineComment("http://localhost:8123/help?html=true (or whatever port you")
                                .lineComment("configure) in a browser");
                        bb.invoke("enableHelp").on("bldr");

                        bb.lineComment("Add this module to the set that will be used");
                        bb.invoke("add").withArgument("this").on("bldr");

                        bb.lineComment("And add the settings, so @Named guice bindings will be set up for them");
                        bb.invoke("add").withArgument("settings").on("bldr");

                        bb.lineComment("Launch the server.  Note that something needs to call await()")
                                .lineComment("on the returned ServerControl or the process will exit immediately.")
                                .lineComment(" ")
                                .lineComment("We do not do that by default, because that would assume that")
                                .lineComment("this server has the process to itself, and nothing else needs")
                                .lineComment("to be started, which is not a safe assumption to make.");
                        bb.returningInvocationOf("start")
                                .onInvocationOf("build")
                                .on("bldr");
                    });
        });
    }

    private void createMainMethod(ClassBuilder<String> cb) {
        cb.method("main")
                .withModifier(PUBLIC, STATIC)
                .throwing("Exception")
                .docComment("Launch the server with no operation interfaces "
                        + "configured.  Visit <a href=\"http://localhost:8123/help?html=true\">"
                        + "localhost:8123/help?html=true</a> to view your API and the steps "
                        + "used to answer a request.\n"
                        + BIG_HONKING_SETTINGS_DOCS_BLOCK
                        + "\n@param args Command-line arguments")
                .addArgument("String[]", "args")
                .body(bb -> {
                    bb.lineComment("Enable debug logging so the port and other information is "
                            + "printed to the console");
                    bb.invoke("setProperty")
                            .withStringLiteral("acteur.debug")
                            .withStringLiteral("true")
                            .on("System");
                    bb.invoke("await").onInvocationOf("start")
                            .withArgument("args")
                            .onNew(nb -> {
                                nb.ofType(cb.className());
                            });
                });
    }

    private void createSettingsMethodAndField(ClassBuilder<String> cb) {
        cb.field("settingsConsumer", fld -> {
            cb.importing(Consumer.class);
            fld.withModifier(PRIVATE)
                    .initializedAsLambda("Consumer<SettingsBuilder>", lb -> {
                        lb.withArgument("settingsBuilder").body(lbb -> {
                            lbb.lineComment("This sets the Server: header in HTTP responses.");
                            lbb.invoke("add")
                                    .withStringLiteral("application.name")
                                    .withStringLiteral(shape.getId().getName()
                                            + " " + shape.getVersion())
                                    .on("settingsBuilder");
                        });
                    });
        });

        cb.method("configuringSettingsWith")
                .docComment("Pass a consumer here to configure defaults for thing such as "
                        + "ServerModule.PORT and similar.\n@param consumer A consumer which "
                        + "can configure the key/value pair settings the server runs with "
                        + "(such as thread pool sizes, ssl configuration, ports and similar)."
                        + "\nNote that this method has no effect unless the server is launched "
                        + "using the {@link " + cb.className() + "#start} method on this instance."
                        + "\n@param consumer A consumer which can add to or modify the settings "
                        + "builder before the server is launched"
                        + "\n@return this")
                .withModifier(PUBLIC)
                .returning(cb.className())
                .addArgument("Consumer<SettingsBuilder>", "consumer")
                .body(bb -> {
                    bb.assign("settingsConsumer")
                            .toInvocation("andThen")
                            .withArgument("consumer")
                            .on("this.settingsConsumer")
                            .returningThis();
                });

    }

    private void createScopeFieldAndGetter(ClassBuilder<String> cb) {
        cb.importing("com.mastfrog.giulius.scope.ReentrantScope");
        cb.field("scope", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .initializedWithNew(nb -> nb.ofType("ReentrantScope"))
                    .ofType("ReentrantScope");
        });
        cb.method("scope").withModifier(PUBLIC).returning("ReentrantScope")
                .docComment("Get the scope used for server requests - some modules that "
                        + "extend the framework may require it as a constructor argument.\n"
                        + "Only used if the server is launched from the start() method."
                        + "\n@return A scope")
                .body(bb -> bb.returningField("scope").ofThis());
    }

    private void createAdditionalModulesMethodAndSet(ClassBuilder<String> cb) {
        cb.field("modules", fld -> {
            cb.importing(List.class, ArrayList.class);
            fld.withModifier(PRIVATE, FINAL)
                    .initializedWithNew(nb -> {
                        nb.withArgument(4).ofType("ArrayList<>");
                    }).ofType("List<Module>");
        });

        cb.method("withModule", mb -> {
            mb.docComment("Provide some additional modules that should be installed when this "
                    + "one is to configure Guice bindings."
                    + "\n@param module A guice module"
                    + "\n@return this");
            mb.addArgument("Module", "module")
                    .withModifier(PUBLIC)
                    .returning(cb.className())
                    .body(bb -> {
                        bb.invoke("add").withArgument("module")
                                .on("modules").returningThis();
                    });
        });
    }

    private static final String BIG_HONKING_SETTINGS_DOCS_BLOCK
            = "<h3>Useful Settings</h3>"
            + "\nThese can be set either in the settings "
            + "consumer, or the raw value passed as a command-line argument prefixed "
            + "with <code>--</code> - for example, <code>--port 9090</code> to set "
            + "<code>ServerModule.PORT</code>.  Note this is an asynchronous framework - a "
            + "<i>very</i> small number of threads can handle thousands of concurrent requests. "
            + "They may also be set using system properties.\n"
            + "\nSettings can also be loaded from a file named $YOUR_SERVICE_NAME.properties which "
            + "will be looked for in /etc, /opt/local/etc, $HOME and ./"
            + "<ul>"
            + "<li><b>ServerModule.PORT</b> - <code>port</code> - the HTTP port</li>"
            + "<li><b>ServerModule.MAX_CONTENT_LENGTH</b> - <code>maxContentLength</code> - set "
            + "a limit on the maximum inbound request body size - this impacts how much "
            + "memory is allocated per request</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_SSL_ENABLED</b> - <code>ssl.enabled</code> - start"
            + "the server using HTTPS (self signed certificate unless you install a module that"
            + "binds your own)</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_CORS_ENABLED</b> - <code>cors.enabled</code> - enable default "
            + "CORS responses for all paths (other properties can set how restrictive "
            + "or not CORS responses are - see the docs for ServerModule)</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_DECODE_REAL_IP</b> - <code>decodeRealIP</code> - "
            + "decode common HTTP proxy headers such <code>X-Real-IP</code> and <code>X-Forwarded-For</code> "
            + "so logging and similar code can report the original URL being serviced</li>"
            + "<li><b>ServerModule.EVENT_THREADS</b> - <code>eventThreads</code> - the number of "
            + "event threads to handle selector events from inbound traffic</li>"
            + "<li><b>ServerModule.WORKER_THREADS</b> - <code>workers</code> - the number of worker threads for generating responses</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_MAX_REQUEST_LINE_LENGTH</b> - <code>max.request.line.length</code> - set"
            + "the maximum line length of an HTTP request line - if you know the maximum possible length of URLs with"
            + "query strings, setting this can save some allocation per request</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_MAX_HEADER_BUFFER_SIZE</b> - <code>max.header.buffer.size</code> - set"
            + "the maximum number of bytes allowed to be consumed by inbound HTTP headers before an error response"
            + "is sent.  If you are not using large cookies, setting this to a small size can also save some"
            + "allocations per-request</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_MAX_CHUNK_SIZE</b> - <code>max.chunk.size</code> - cap the "
            + "size of inbound chunks that use HTTP chunked content-encoding</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_BASE_PATH</b> - <code>basepath</code> - a base "
            + "<li><b>ServerModule.HTTP_COMPRESSION_LEVEL</b> - <code>compression.level</code> - the HTTP compression level</li>"
            + "path prepended to all HTTP urls served - note no change is needed in the position of arguments we parse for - it"
            + "is trimmed before paths are passed for evaluation</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_URLS_HOST_NAME</b> - <code>hostname</code> - host name, for use in redirect URLs</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_URLS_EXTERNAL_PORT</b> - <code>external.port</code> - the external HTTP port, for generating redirect URLs</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_URLS_EXTERNAL_SECURE_PORT</b> - <code>external.secure.port</code> - the external HTTPS port, for generating redirect URLs</li>"
            + "<li><b>ServerModule.SETTINGS_KEY_GENERATE_SECURE_URLS</b> - <code>secure.urls</code> - Use the secure port when generating redirect URLs by default</li>";

    private void createInitializedCheck(ClassBuilder<String> cb) {
        cb.field("initialized", fld -> {
            fld.withModifier(PRIVATE, VOLATILE)
                    .ofType("boolean");
        });
        cb.method("checkInitialized", mth -> {
            mth.withModifier(PRIVATE)
                    .docComment("Used from methods that modify the set of bound types if it is invoked "
                            + "after the injector has been created (after which, such a call cannot possibly "
                            + "accomplish anything)."
                            + "\n@throws IllegalStateException if configure() has already been called.")
                    .body(bb -> {
                        bb.lineComment("Throw if this method is invoked after configure() has been called.");
                        bb.iff().booleanExpression("initialized")
                                .andThrow(nb -> {
                                    nb.withStringLiteral("This method cannot be called after the injector has been initialized.")
                                            .ofType("IllegalStateException");
                                }).endIf();
                    });
        });
    }

    private String exceptionEvaluatorListTypeName() {
        return typeNameOf(shape) + "ExceptionEvaluators";
    }

    private void createExceptionEvaluationExtensions(ClassBuilder<String> cb) {
        String tn = exceptionEvaluatorListTypeName();
        String baseFunctionType = "Function<? super Throwable, ? extends Optional<ErrorResponse>>";
        cb.innerClass(tn, in -> {
            in.docComment("Exception evaluator functions supplied by the application - this "
                    + "class gives us a type to bind which cannot possibly conflict with any "
                    + "bound type by any other module, since the type is not visible outside "
                    + "the scope of this class.");
            in.withModifier(PRIVATE, STATIC, FINAL)
                    .extending("ArrayList<" + baseFunctionType + ">");

            in.importing(ArrayList.class, Function.class, Optional.class);
            in.constructor(con -> {
                con.body(bb -> {
                    bb.invoke("super").withArgument(8).inScope();
                });
            });
        });
        cb.field(decapitalize(tn), fld -> {
            fld.initializedWithNew(nb -> nb.ofType(tn)).ofType(tn);
        });
        cb.method("mappingExceptionTo", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Provide a custom responses for exceptions thrown during "
                            + "request processing.  The defaults for invalid JSON and constraint "
                            + "violations are built-in; if your implementation calls code that "
                            + "can throw other kinds of exceptions that should not be mapped to "
                            + "<code>500 Internal Server Error</code>, provide custom handling for "
                            + "them here."
                            + "\n@param converter A function that converts an exception into an error response")
                    .addArgument(baseFunctionType, "converter")
                    .returning(cb.className())
                    .body(bb -> {
                        bb.ifNull("converter")
                                .andThrow(nb -> {
                                    nb.withStringLiteral("Converter may not be null.")
                                            .ofType("IllegalArgumentException");
                                }).endIf();
                        bb.invoke("add")
                                .withArgument("converter")
                                .onField(decapitalize(tn)).ofThis();
                        bb.returningThis();
                    });
        });

        cb.method("mappingExceptionTo", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Simplified custom error responses for exceptions thrown during "
                            + "request processing.  Pass in an exception type and the HTTP response "
                            + "code it should be converted to. The defaults for invalid JSON and constraint "
                            + "violations are built-in; if your implementation calls code that "
                            + "can throw other kinds of exceptions that should not be mapped to "
                            + "<code>500 Internal Server Error</code>, provide custom handling for "
                            + "them here.\n"
                            + "Note: The order these are added in is important - if you pass a catch-all type "
                            + "like <code>Throwable</code> here, and then call this method with a more specific type, "
                            + "the more specific type will never be checked."
                            + "\n@param thrownType An exception subtype."
                            + "\n@param status The http status to return")
                    .addArgument("Class<? extends Throwable>", "thrownType")
                    .addArgument("HttpResponseStatus", "status")
                    .returning(cb.className())
                    .body(bb -> {
                        validationExceptions().createNullCheck("thrownType", cb, bb);
                        validationExceptions().createNullCheck("status", cb, bb);

                        /*
        this.mappingExceptionTo(thrown -> {
            if (thrownType.isInstance(thrown)) {
                return Optional.of(ErrorResponse.create(status, thrown.getMessage()));
            }
            return Optional.empty();
        });
                         */
                        bb.returningInvocationOf("mappingExceptionTo")
                                .withLambdaArgument(lb -> {
                                    lb.withArgument("thrown")
                                            .body(lbb -> {
                                                Value test = invocationOf("isInstance")
                                                        .withArgument("thrown")
                                                        .on("thrownType");
                                                lbb.iff(test)
                                                        .returningInvocationOf("of")
                                                        .withArgumentFromInvoking("create")
                                                        .withArgument("status")
                                                        .withArgumentFromInvoking("getMessage")
                                                        .on("thrown")
                                                        .on("ErrorResponse")
                                                        .on("Optional")
                                                        .endIf();
                                                lbb.returningInvocationOf("empty").on("Optional");
                                            });
                                }).onThis();

                    });
        });

        cb.method("mappingExceptionTo", mth -> {
            mth.withModifier(PUBLIC)
                    .docComment("Simplified custom error responses for exceptions thrown during "
                            + "request processing.  Pass in an exception type and the HTTP response "
                            + "code it should be converted to. The defaults for invalid JSON and constraint "
                            + "violations are built-in; if your implementation calls code that "
                            + "can throw other kinds of exceptions that should not be mapped to "
                            + "<code>500 Internal Server Error</code>, provide custom handling for "
                            + "them here."
                            + "\n@param thrownType An exception subtype."
                            + "\n@param status The http status to return")
                    .addArgument("Class<? extends Throwable>", "thrownType")
                    .addArgument("int", "status")
                    .returning(cb.className())
                    .body(bb -> {
                        Variable v = variable("status");
                        bb.iff(v.isLessThan(number(100)).logicalOrWith(v.isGreaterThan(number(599))))
                                .andThrow(nb -> {
                                    nb.withStringConcatentationArgument("Not a valid HTTP response code: ")
                                            .appendExpression("status")
                                            .endConcatenation()
                                            .ofType("IllegalArgumentException");
                                }).endIf();
                        bb.returningInvocationOf("mappingExceptionTo")
                                .withArgument("thrownType")
                                .withArgumentFromInvoking("valueOf")
                                .withArgument("status")
                                .on("HttpResponseStatus")
                                .onThis();
                    });
        });

    }

    private void generateAuthBindings(ClassBuilder<?> cb, BlockBuilder<?> bb, Set<OperationShape> operations, String binder) {
        withBindingInfo(operations, (Set<String> allMechanisms,
                Map<ShapeId, Set<OperationShape>> operationsForPayload,
                Set<ShapeId> allPayloadTypes, Set<ShapeId> optionalTypes) -> {

            Set<String> nonNullableAuthTypes = new TreeSet<>();
            Set<String> nullableAuthTypes = new TreeSet<>();
            for (ShapeId id : allPayloadTypes) {
                withAuthTypeInfo(cb, id, (pkg, ifName, fieldName) -> {

                    bb.lineComment("Auth payload type " + id + " -> " + ifName);

                    String clazz = "Class<? extends " + ifName + ">";

                    bb.ifNotNull(fieldName)
                            .invoke("to")
                            .withArgument(fieldName)
                            .onInvocationOf("bind").withClassArgument(ifName)
                            .on(binder)
                            .endIf();

                    Shape payloadShape = model.expectShape(id);

                    String payloadPackage = names().packageOf(payloadShape);
                    String payloadType = typeNameOf(payloadShape, true);

                    cb.importing(payloadPackage + "." + payloadType);

                    if (optionalTypes.contains(id)) {
                        nullableAuthTypes.add(payloadType);
                    } else {
                        nonNullableAuthTypes.add(payloadType);
                    }
                });
            }
            bb.lineComment("Nullable auth types : " + nullableAuthTypes);
            bb.lineComment("Non-Nullable auth types : " + nonNullableAuthTypes);
            if (!nullableAuthTypes.isEmpty()) {

                // scope.bindTypes(binder, types);
//                scope.bindTypesAllowingNulls(binder, types);
                InvocationBuilder<?> inv = bb.invoke("bindTypesAllowingNulls")
                        .withArgument(binder);
                for (String na : nullableAuthTypes) {
                    inv = inv.withArgument(na + ".class");
                }
                inv.on("scope");
            }
            if (!nonNullableAuthTypes.isEmpty()) {
                InvocationBuilder<?> inv = bb.invoke("bindTypes")
                        .withArgument(binder);
                for (String na : nonNullableAuthTypes) {
                    inv = inv.withArgument(na + ".class");
                }
                inv.on("scope");
            }
        });
    }

    interface AuthTypeInfoConsumer {

        void accept(String pkg, String ifName, String fieldName);
    }

    void withAuthTypeInfo(ClassBuilder<?> cb, ShapeId authPayloadType, AuthTypeInfoConsumer c) {
        String pkg = authPackage(shape, names());
        String ifName = "AuthenticateWith" + typeNameOf(authPayloadType);
        String[] fqns = new String[]{pkg + "." + ifName};
        maybeImport(cb, fqns);
        String fieldName = decapitalize(ifName) + "Type";
        c.accept(pkg, ifName, fieldName);
    }

    interface AuthInfoConsumer {

        void accept(Set<String> allMechanisms, Map<ShapeId, Set<OperationShape>> operationsForPayload,
                Set<ShapeId> allPayloadTypes, Set<ShapeId> optionalTypes);
    }

    private void addBindingFieldsAndMethodsForAuthenticators(Set<OperationShape> operations, ClassBuilder<String> cb) {
        withBindingInfo(operations, (Set<String> allMechanisms, Map<ShapeId, Set<OperationShape>> operationsForPayload,
                Set<ShapeId> allPayloadTypes, Set<ShapeId> optionalTypes) -> {
            for (ShapeId id : allPayloadTypes) {
                withAuthTypeInfo(cb, id, (pkg, ifName, fieldName) -> {
                    String clazz = "Class<? extends " + ifName + ">";
                    cb.field(fieldName).withModifier(PRIVATE).ofType(clazz);
                    cb.method("withAuthenticatorFor" + ifName, mth -> {
                        mth.docComment("Bind the authenticator interface for operations which are authenticated "
                                + "and wrap the authenticated identity in an instance of <code>" + typeNameOf(id) + "</code>."
                                + "A subtype of <code>" + ifName + "</code> <b>must</b> bound for requests requiring this "
                                + "kind of authentication to work."
                                + "\n@param type The type"
                                + "\n@return this");
                        mth.withModifier(PUBLIC)
                                .addArgument(clazz, "type")
                                .returning(cb.className())
                                .body(bb -> {
                                    generateTypeFieldAssignment(bb, ifName, cb, fieldName);
                                });
                    });
                });
            }
        });
    }

    private void withBindingInfo(Set<OperationShape> operations, AuthInfoConsumer c) {

        ResourceGraph rg = ensureGraphs(model, shape);
        Set<Shape> allOps = rg.filteredClosure(shape, sh -> sh.getType() == OPERATION);

        Set<String> mechanisms = new TreeSet<>();
        Map<ShapeId, Set<OperationShape>> operationsForPayload = new HashMap<>();
        Set<ShapeId> allPayloadTypes = new HashSet<>();
        Set<ShapeId> optionalTypes = new HashSet<>();

        for (Shape opId : allOps) {
            OperationShape op = opId.asOperationShape().get();
            op.getTrait(AuthenticatedTrait.class).ifPresent(authTrait -> {
                mechanisms.add(authTrait.getMechanism().toLowerCase());
                if (authTrait.isOptional()) {
                    optionalTypes.add(authTrait.getPayload());
                }
                allPayloadTypes.add(authTrait.getPayload());
                operationsForPayload.computeIfAbsent(authTrait.getPayload(), p -> new HashSet<>()).add(op);
            });
        }
        c.accept(mechanisms, operationsForPayload, allPayloadTypes, optionalTypes);

    }
}
