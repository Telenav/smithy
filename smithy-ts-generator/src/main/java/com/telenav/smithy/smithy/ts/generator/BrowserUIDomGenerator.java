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
package com.telenav.smithy.smithy.ts.generator;

import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import com.mastfrog.util.strings.Strings;
import static com.mastfrog.util.strings.Strings.camelCaseToDelimited;
import static com.mastfrog.util.strings.Strings.capitalize;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.smithy.ts.generator.BrowserSDKGenerator.serviceClientName;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.FunctionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSBlockBuilderBase;
import com.telenav.smithy.ts.vogon.TypescriptSource.TSGetterBlockBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import static com.telenav.smithy.ts.vogon.TypescriptSource.typescript;
import com.telenav.smithy.utils.ResourceGraphs;
import java.nio.file.Path;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_DECIMAL;
import static software.amazon.smithy.model.shapes.ShapeType.BIG_INTEGER;
import static software.amazon.smithy.model.shapes.ShapeType.FLOAT;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

/**
 *
 * @author Tim Boudreau
 */
public class BrowserUIDomGenerator extends AbstractTypescriptGenerator<ServiceShape> {

    BrowserUIDomGenerator(ServiceShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    private void importComponent(String comp, TypescriptSource src) {
        src.importing(comp).from("./domstuff");
    }

    private void importComponents(TypescriptSource src, String... comps) {
        for (String c : comps) {
            importComponent(c, src);
        }
    }

    private void importModelObject(String comp, TypescriptSource src) {
        String modelSource = "./" + escape(shape.getId().getName() + "Model");
        src.importing(comp).from(modelSource);
    }

    private void importModelObjects(TypescriptSource src, String... comps) {
        for (String c : comps) {
            importModelObject(c, src);
        }
    }

    private void generateUiModelInterface(TypescriptSource src) {
        src.declareInterface("UIModel<T, O>", iface -> {
            // validate(validationConsumer: (objectPath: string, problem: string) => void)

            iface.method("validate", mth -> {
                mth.docComment("Validate the current values in the model against the "
                        + "constraints in the schema, invoking the passed callback with "
                        + "any problems.")
                        .withArgument("validationConsumer")
                        .ofFunctionType()
                        .withArgument("objectPath").ofType("string")
                        .withArgument("problem").ofType("string")
                        .returningVoid();

            });
            // public listen(listener: () => void) {
            iface.method("listen")
                    .docComment("Obtain notifications on user input")
                    .withArgument("listener")
                    .ofFunctionType(fun -> fun.returningVoid())
                    .returningVoid();

            iface.property("model")
                    .docComment("A new model generated from the input fields in the UI.")
                    .ofType("T | undefined | null");

            iface.method("submit", mth -> {
                mth.docComment("Submit a request using the passed client, returning "
                        + "a promise for its output."
                        + "\n@param client A service client"
                        + "\n@param cancelAnyOutstandingRequests if true, abort any in-flight requests"
                        + "\n@return A promise");
                mth.withArgument("client").ofType(serviceClientName(shape));
                mth.withArgument("cancelAnyOutstandingRequests")
                        .optional().ofType("boolean");
                mth.returning("Promise<O>");
            });
        });
    }

    private String generateServiceClientHolder(String urlTextFieldVarName,
            String spinnerVarName, TypescriptSource src) {
        // An object that holds an instance of the service client, but replaces it if
        // the value of the url field changes
        importComponents(src, "InteractiveComponent", "Startable");
        String cli = serviceClientName(shape);
        String cliPath = "./" + cli;
        String clientFactoryMethodName = decapitalize(cli);
        String result = "clientHolder";
        // Client interface and file name will be the same
        src.importing(cli).from(cliPath);
        // Factory method will be the decapitalized client name
        src.importing(decapitalize(cli)).from(cliPath);
        src.declareClass(serviceClientHolderName(), cl -> {
            cl.docComment("Constructs instances of `" + cli
                    + "`, replacing the cached instance if the url field's value changes.");
            cl.property("urlField").readonly().setPrivate().ofType("InteractiveComponent<string>");
            cl.property("lastUrl").setPrivate().optional().ofType("string");
            cl.property("_client").setPrivate().optional().ofType(cli);
            cl.property("progressIndicator").setPrivate().optional().ofType("Startable");
            cl.constructor(con -> {
                con.withArgument("urlField").ofType("InteractiveComponent<string>");
                con.withArgument("progressIndicator").optional()
                        .ofType("Startable");
                con.body(bb -> {
                    bb.statement("this.urlField = urlField");
                    bb.statement("this.progressIndicator = progressIndicator");
                });
            });

            cl.method("onClientCreated", mth -> {
                mth.withArgument("client").ofType(cli);
                TsBlockBuilder<Void> block = mth.returning(cli);
                ConditionalClauseBuilder<TsBlockBuilder<Void>> test = block.iff("this.progressIndicator");
                test.invoke("listen")
                        .withLambda()
                        .withArgument("requestId").inferringType()
                        .withArgument("msg").inferringType()
                        .withArgument("running").inferringType()
                        .withArgument("request").inferringType()
                        .withArgument("event").inferringType()
                        .body(bb -> {
                            bb.iff("this.progressIndicator")
                                    .statement("this.progressIndicator.running = running > 0")
                                    .endIf();
                        }).onField("serviceClient").of("client")
                        .endIf();

                block.returning("client");
            });

            cl.method("urlChanged", mth -> {
                mth.makePrivate().withArgument("url").ofType("string")
                        .returning("boolean")
                        .iff("this.lastUrl !== url")
                        .statement("this.lastUrl = url")
                        .returning("true")
                        .returning("false");
            });

            cl.getter("client", mth -> {
                mth.declare("u").ofType("string")
                        .assignedToInvocationOf("value")
                        .onField("urlField")
                        .ofThis();
                mth.declare("changed")
                        .ofType("boolean")
                        .assignedToInvocationOf("urlChanged")
                        .withArgument("u")
                        .onThis();
                mth.iff("changed || !this._client", iff -> {
                    iff.statement("this._client = this.onClientCreated(" + clientFactoryMethodName + "(u === '' ? null : u))");
                });
                mth.returning("this._client");
            });
        });
        src.declareConst(result)
                .assignedToNew()
                .withArgument(urlTextFieldVarName)
                .ofType(serviceClientHolderName());

        return result;
    }

    private String serviceClientHolderName() {
        return escape(shape.getId().getName() + "ClientHolder");
    }

    private static final String MODEL_FOR_PANEL_MAP = "modelForPanel";
    private static final String URL_PANEL_VAR = "urlPanel";
    private static final String SPINNER_VAR = "spinner";

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = typescript("testit");
        generateUiModelInterface(src);
        importComponents(src, "Row", "Panel", "NavPanel",
                "ProblemsPanel", "StaticText", "EventType",
                "TextField", "Spinner", "Button");

        src.invoke("attach")
                .withStringLiteralArgument("top")
                .onNew(nb -> {
                    String title = capitalize(camelCaseToDelimited(shape.getId().getName(), ' '))
                            + " Minimal Web UI";
                    nb.withStringLiteralArgument(title)
                            .withStringLiteralArgument("h1")
                            .ofType("StaticText");
                });

        src.declare(CONTENT_HEAD_VAR)
                .assignedToNew()
                .withStringLiteralArgument("-")
                .withStringLiteralArgument("h2")
                .ofType("StaticText");

        src.invoke("attach").withStringLiteralArgument("content_head")
                .on(CONTENT_HEAD_VAR);

        src.declare(SPINNER_VAR)
                .assignedToNew()
                .withStringLiteralArgument("networkActivity")
                .ofType("Spinner");

        src.declare(URL_PANEL_VAR)
                .assignedToInvocationOf("withStyles")
                .withStringLiteralArgument("urlpanel")
                .onInvocationOf("labeledWith")
                .withStringLiteralArgument("Service URI")
                .onNew()
                .withStringLiteralArgument("urlfield")
                .ofType("TextField");

        // Attach the spinner inside the url panel so it is a child of it
        // and gets the same float-right positioning
        src.invoke("attach")
                .withStringLiteralArgument("top")
                .on(SPINNER_VAR);

        String clientHolderVar = generateServiceClientHolder(URL_PANEL_VAR, SPINNER_VAR, src);

        src.invoke("attach")
                .withStringLiteralArgument("top")
                .on(URL_PANEL_VAR);

        src.declareConst(SUBMIT_BUTTON_VAR).ofType("Button").assignedToNew()
                .withStringLiteralArgument("button")
                .withStringLiteralArgument("submitter")
                .withStringLiteralArgument("Submit Request")
                .ofType("Button");

        src.statement(SUBMIT_BUTTON_VAR + ".enabled = false");

//        src.invoke("listen")
//                .withLambda(lb -> {
//                    lb.withArgument("event").ofType("EventType<string>")
//                            .withArgument("id").ofType("string")
//                            .withArgument("value").ofType("string");
//                    lb.body(bb -> {
//                        bb.invoke("log")
//                                .withStringLiteralArgument("URI change ")
//                                .on("console");
//                    });
//                }).on("urlPanel");
        String navVar = "mainNavigation";

        src.blankLine().lineComment("A navigation panel that lists operations:");
        src.declareConst(navVar)
                .ofType("NavPanel")
                .assignedToNew()
                .withStringLiteralArgument("nav")
                .withStringLiteralArgument("content")
                .ofType("NavPanel");

        src.declareConst(PROBLEMS_PANEL_VAR)
                .assignedToNew()
                .withStringLiteralArgument("problemsInner")
                .ofType("ProblemsPanel");

        src.declareConst(MODEL_FOR_PANEL_MAP)
                .assignedToNew().ofType("Map<string, UIModel<any, any>>");

        src.invoke("attach").withStringLiteralArgument("probs").on(PROBLEMS_PANEL_VAR);
        src.invoke("listen")
                .withLambda().withArgument("title").ofType("string")
                .withArgument("panel").ofType("Panel")
                .body(lbb -> {
                    lbb.invoke("clear").on(PROBLEMS_PANEL_VAR);
                    lbb.declareConst("panelModel")
                            .ofType("UIModel<any, any> | undefined")
                            .assignedToInvocationOf("get")
                            .withArgumentFromField("id")
                            .of("panel")
                            .on(MODEL_FOR_PANEL_MAP);

                    ConditionalClauseBuilder<TsBlockBuilder<Void>> test
                            = lbb.iff("panelModel");
                    test.declareConst("valid").ofType("boolean")
                            .assignedToInvocationOf("validate")
                            .withLambda(lub -> {
                                lub.withArgument("objectPath").ofType("string")
                                        .withArgument("problem").ofType("string");
                                lub.body(lubb -> {
                                    lubb.invoke("add")
                                            .withArgument("objectPath")
                                            .withArgument("problem")
                                            .on(PROBLEMS_PANEL_VAR);
                                });
                            })
                            .on("panelModel");

                    test.statement(SUBMIT_BUTTON_VAR + ".enabled = valid");
                    test.endIf();

                    lbb.statement("contentHead.text = title");
                }).on(navVar);

        ContainerFactory panels = new ContainerFactory("panel", "Panel", src);
        ContainerFactory rows = new ContainerFactory("row", "Row", src);

        FunctionBuilder<TypescriptSource> createUI = src.function("populateUI")
                .withArgument(navVar)
                .ofType("NavPanel")
                .withArgument(MODEL_FOR_PANEL_MAP).ofType("Map<string, UIModel<any, any>>");

        src.blankLine().lineComment("Creates and adds panels to the navigation component.")
                .lineComment("Their contents will not be added to the DOM until the first time")
                .lineComment("they are used.");

        src.lineComment("Build the panels for each operation, and add them to the nav panel.")
                .lineComment("The first one added will be immediately realized on-screen.");
        src.blankLine().invoke("populateUI").withArgument(navVar)
                .withArgument(MODEL_FOR_PANEL_MAP)
                .inScope();

        TsBlockBuilder<TypescriptSource> createUiBody = createUI.body();
        List<InterfaceBuilder<TypescriptSource>> ifaces = new ArrayList<>();
        List<Runnable> blocks = new ArrayList<>();

        List<Runnable> lasts = new ArrayList<>();
        panels.withBlock(createUiBody, () -> {

            panels.onNewItem((varName, op) -> {
                if (op != null) {
                    String nm = capitalize(camelCaseToDelimited(op.getId().getName(), ' '));
                    createUiBody.invoke("add")
                            .withStringLiteralArgument(nm)
                            .withArgument(varName)
                            .on(navVar);
                }
            });

            // Use a consistent sort order that will sort by name in the common
            // case that we are only dealing with one smithy namespace
            TreeSet<OperationShape> ops = new TreeSet<OperationShape>((a, b) -> {
                return a.getId().compareTo(b.getId());
            });

            ops.addAll(ResourceGraphs.graph(model, shape)
                    .transformedClosure(shape, sh
                            -> sh.asOperationShape().orElse(null)));

            Map<OperationShape, String> panelForOp = new HashMap<>();
//            src.generateDebugLogCode();
            for (OperationShape op : ops) {
                String pnl = generateUiCodeForOp(op, panels, rows, src, ifaces, blocks, createUiBody, lasts);
                panelForOp.put(op, pnl);
            }
        });

        src.blankLine().lineComment("This will cause the first added panel to become selected,")
                .lineComment("which triggers adding all child components for that operation")
                .lineComment("to the DOM.");
        src.invoke("attach")
                .withStringLiteralArgument("navholder")
                .on(navVar);
        src.invoke("attach")
                .withStringLiteralArgument("submission")
                .on(SUBMIT_BUTTON_VAR);

        createUiBody.endBlock();

        ifaces.forEach(InterfaceBuilder::close);
        blocks.forEach(blk -> blk.run());
        lasts.forEach(blk -> blk.run());

        c.accept(src);
    }
    public static final String CONTENT_HEAD_VAR = "contentHead";
    public static final String PROBLEMS_PANEL_VAR = "problems";
    public static final String SUBMIT_BUTTON_VAR = "submitButton";

    private String generateUiCodeForOp(OperationShape op, ContainerFactory panels,
            ContainerFactory rows, TypescriptSource bb,
            List<InterfaceBuilder<TypescriptSource>> ifaces,
            List<Runnable> blocks, TsBlockBuilder<TypescriptSource> createUiBody,
            List<Runnable> lasts) {

        createUiBody.blankLine().blankLine().lineComment(op.getId().toString());
        String panel = panels.next(op);

        op.getInput().flatMap(inp -> model.expectShape(inp).asStructureShape())
                .ifPresent(input -> {
                    generateInputUIFor(panel, op, rows, input, bb, ifaces, blocks,
                            createUiBody, lasts);
                });

        return panel;
    }

    private static final String RAW_MODEL_VAR = "rawModel";

    private void generateInputUIFor(String panel, OperationShape op,
            ContainerFactory rows, StructureShape input, TypescriptSource src,
            List<InterfaceBuilder<TypescriptSource>> ifaces,
            List<Runnable> blocks, TsBlockBuilder<TypescriptSource> createUiBody,
            List<Runnable> lasts) {

        String uiModelType = escape(op.getId().getName() + "UIModel");

        String outputType = op.getOutput().map(out -> tsTypeName(model.expectShape(out)))
                .orElse("void");

        try (ComponentAdder add = new ComponentAdder(panel, 3, rows, src)) {
            InterfaceBuilder<TypescriptSource> iface
                    = src.declareInterface(escape(op.getId().getName() + "UIModel"));

            importModelObject(tsTypeName(input), src);
            ClassBuilder<TypescriptSource> ifaceImpl
                    = src.declareClass(op.getId().getName() + "UIModelImpl")
                            .implementing(iface.name())
                            .implementing("UIModel<" + tsTypeName(input) + ","
                                    + outputType + ">");

            TSGetterBlockBuilder<ClassBuilder<TypescriptSource>> modelGetter
                    = ifaceImpl.getter("model")
                            .declare(RAW_MODEL_VAR).ofType("object").assignedTo("{}")
                            .withExplicitReturnType(tsTypeName(input));

            TsBlockBuilder<ClassBuilder<TypescriptSource>> valFunc
                    = ifaceImpl.method("validate")
                            .docComment("Validate the model.")
                            .withArgument("validationConsumer")
                            .ofFunctionType(fsb -> {
                                fsb.withArgument("objectPath").ofType("string")
                                        .withArgument("problem").ofType("string")
                                        .returning("void");
                            }).returning("boolean")
                            .statement("let result : boolean = true")
                            .invoke("clear").on(PROBLEMS_PANEL_VAR);

            ifaces.add(iface);
            LinkedList<String> idPath = new LinkedList<>(
                    asList(input.getId().getName(), op.getId().getName()));

            FunctionBuilder<TypescriptSource> func
                    = src.function("populate" + capitalize(panel));
            func.withArgument(panel)
                    .ofType("Panel")
                    .withArgument("validationListener")
                    .ofFunctionType(fu -> {
                        fu.withArgument("what").ofType("string")
                                .withArgument("problem").ofType("string")
                                .returning("void");
                    })
                    .returning(ifaceImpl.name());

            TsBlockBuilder<TypescriptSource> block = func.body();

            Map<String, String> localFieldForIfaceField = new TreeMap<>();

            rows.withBlock(block, () -> {
                drillThroughShapes(RAW_MODEL_VAR, idPath,
                        add, input, src, iface, block, localFieldForIfaceField, valFunc,
                        ifaceImpl, modelGetter);
                add.close();
            });

            String modelInstance = escape(decapitalize(op.getId().getName()) + "ModelInstance");
            createUiBody.declareConst(modelInstance)
                    .ofType(ifaceImpl.name())
                    .assignedToInvocationOf("populate" + capitalize(panel))
                    .withArgument(panel)
                    .withLambda(lb -> {
                        lb.withArgument("what").ofType("string")
                                .withArgument("description").ofType("string");
                        lb.body().invoke("add")
                                .withArgument("what")
                                .withArgument("description")
                                .on(PROBLEMS_PANEL_VAR)
                                .endBlock();
                    })
                    .inScope();

            createUiBody.invoke("set")
                    .withArgumentFromField("id")
                    .of(panel)
                    .withArgument(modelInstance)
                    .on(MODEL_FOR_PANEL_MAP);

            block.declareConst("__result")
                    .ofType(iface.name())
                    .assignedToObjectLiteral(olb -> {
                        localFieldForIfaceField.forEach((name, type) -> {
                            olb.assigning(name).toExpression(name);
                        });
                    });

            ifaceImpl.method("listen", mth -> {
                mth.makePublic()
                        .docComment("Listen for user interaction induced changes in any "
                                + " interactive component in this model."
                                + "\n@param listener a no-arg, no-return function")
                        .withArgument("listener")
                        .ofFunctionType(fb -> {
                            fb.returning("void");
                        });
                mth.body(mbb -> {
                    localFieldForIfaceField.forEach((name, type) -> {
                        mbb.invoke("listen")
                                .withLambda()
                                .withArgument("_event").inferringType()
                                .withArgument("_kind").inferringType()
                                .withArgument("_value").inferringType()
                                .body(lbb -> {
                                    lbb.invoke("listener").inScope();
                                })
                                .onField(name).ofThis();
                    });
                });
            });

            ifaceImpl.method("submit", mth -> {
                mth.withArgument("client").ofType(serviceClientName(shape));
                mth.withArgument("cancelAnyOutstandingRequests")
                        .optional().ofType("boolean");
                TsBlockBuilder<?> bb = mth.returning("Promise<" + outputType + ">");
                bb.iff("cancelAnyOutstandingRequests")
                        .invoke("cancelAll")
                        .onField("serviceClient")
                        .of("client")
                        .endIf();
                importModelObject(outputType, src);
                String methodName = escape(decapitalize(op.getId().getName()));
                bb.returningInvocationOf(methodName)
                        .withArgumentFromField("model").ofThis()
                        .on("client");
            });

            ifaceImpl.constructor(con -> {
                con.withArgument("stub").ofType(iface.name());
                con.body(cbb -> {
                    localFieldForIfaceField.forEach((name, type) -> {
                        cbb.statement("this." + name + " = stub." + name);
                    });
                });
            });

            // Order is important here, or typescript will complain about
            // forward declarations
            blocks.add(() -> {
                block.declareConst("finalResult")
                        .assignedToNew()
                        .withArgument("__result")
                        .ofType(ifaceImpl.name());

                block.lineComment("DO IT HERE ---");
                block.invoke("listen")
                        .withLambda()
                        .body()
                        .invoke("validate")
                        .withArgument("validationListener")
                        .on("finalResult")
                        .endBlock()
                        .on("finalResult");

                block.returning("finalResult");

                modelGetter.returningInvocationOf("fromJsonObject")
                        .withArgument("rawModel")
                        .on(tsTypeName(input));

//                modelGetter.statement("throw new Error('unimplemented')").endBlock();
//                block.returningNew()
//                        .withArgument("__result")
//                        .ofType(ifaceImpl.name());
//                src.blankLine();
            });
            lasts.add(() -> {
                valFunc.returning("result");
                ifaceImpl.close();
            });
        }
    }

    private String jsIdFromStrings(List<? extends String> l) {
        return Strings.join('.', l);
    }

    private String varNameFromStrings(List<? extends String> l) {
//        return Strings.join('_', l);
        List<String> copy = new ArrayList<>(l.subList(0, l.size() - 1));
        Collections.reverse(copy);
        return Strings.join('_', copy);
    }

    private String displayNameFromStrings(List<? extends String> l) {
        List<String> copy = new ArrayList<>(l.subList(0, l.size() - 2));
        Collections.reverse(copy);
        for (int i = 0; i < copy.size(); i++) {
            String item = copy.get(i);
            String friendly = Strings.camelCaseToDelimited(item, ' ');
            StringBuilder words = new StringBuilder();
            for (String f : friendly.split("\\s+")) {
                if (words.length() > 0) {
                    words.append(' ');
                }
                words.append(capitalize(f));
            }
            copy.set(i, words.toString());
        }
        return Strings.join(" ➜ ", copy);
    }

    private String rawModelNameFor(String memberName) {
        return escape(decapitalize(memberName + "RawValue"));
    }

    private String rawTypeFor(Shape shape) {
        return jsTypeOf(shape);
    }

    private void drillThroughShapes(String rawModelVariable, LinkedList<String> idPath, ComponentAdder adder,
            StructureShape input, TypescriptSource src,
            InterfaceBuilder<TypescriptSource> iface, TsBlockBuilder<TypescriptSource> bb,
            Map<String, String> localFieldForIfaceField,
            TsBlockBuilder<ClassBuilder<TypescriptSource>> validation,
            ClassBuilder<TypescriptSource> ifaceImpl,
            TSGetterBlockBuilder<ClassBuilder<TypescriptSource>> modelGetter) {

        importComponent("LabeledComponent", src);
        for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {
            idPath.push(e.getKey());
            try {
                String name = varNameFromStrings(idPath);
                String jsid = jsIdFromStrings(idPath);
                Shape target = model.expectShape(e.getValue().getTarget());

                String rawType = rawTypeFor(target);

                boolean required = e.getValue().hasTrait(RequiredTrait.class);

                bb.blankLine().blankLine().lineComment("Shape " + target.getType() + " " + target.getId().getName())
                        .lineComment("For member " + e.getKey() + " of " + input.getId().getName())
                        .lineComment("Var " + name);

                String jsonName = e.getValue().getMemberTrait(model, JsonNameTrait.class)
                        .map(JsonNameTrait::getValue).orElse(e.getKey());

                String itemType;
                switch (target.getType()) {
                    case STRUCTURE:
                        String submodelName = rawModelNameFor(e.getKey());
                        modelGetter.declare(submodelName)
                                .ofType("object")
                                .assignedTo("{}");
                        drillThroughShapes(submodelName, idPath,
                                adder, target.asStructureShape().get(), src,
                                iface, bb, localFieldForIfaceField, validation,
                                ifaceImpl, modelGetter);
                        if (!required) {
                            modelGetter.iff("Object.keys(" + submodelName + ").length > 0")
                                    .statement(rawModelVariable + "['" + jsonName
                                            + "'] = " + submodelName);
                        } else {
                            modelGetter.statement(rawModelVariable + "['" + jsonName
                                    + "'] = " + submodelName);
                        }
                        continue;
                    case BOOLEAN:
                        importComponent("Checkbox", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<boolean>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew()
                                .withStringLiteralArgument(jsid)
                                .ofType("Checkbox");
                        iface.property(name)
                                .ofType("LabeledComponent<boolean>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<boolean>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case BIG_INTEGER:
                    case INTEGER:
                    case LONG:
                    case SHORT:
                    case BYTE:
                        importComponent("IntegerField", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<number>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew()
                                .withStringLiteralArgument(jsid)
                                .ofType("IntegerField");
                        iface.property(name)
                                .ofType("LabeledComponent<number>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<number>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case BIG_DECIMAL:
                    case DOUBLE:
                    case FLOAT:
                        importComponent("FloatField", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<number>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew()
                                .withStringLiteralArgument(jsid)
                                .ofType("FloatField");
                        iface.property(name)
                                .ofType("LabeledComponent<number>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<number>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case TIMESTAMP:
                        importComponent("DateTimePicker", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<Date>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew()
                                .withStringLiteralArgument(jsid)
                                .ofType("DateTimePicker");
                        iface.property(name)
                                .ofType("LabeledComponent<Date>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<Date>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case STRING:
                        importComponent("TextField", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<string>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew(nb -> {
                                    nb.withStringLiteralArgument(name)
                                            .withStringLiteralArgument(jsid)
                                            .ofType("TextField");
                                });
                        iface.property(name)
                                .ofType("LabeledComponent<string>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<string>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case ENUM:
                        importComponent("ComboBox", src);
                        bb.declare(name)
                                .ofType(itemType = "LabeledComponent<string>")
                                .assignedToInvocationOf("labeledWith")
                                .withStringLiteralArgument(displayNameFromStrings(idPath))
                                .onNew(nb -> {
                                    nb.withStringLiteralArgument(name)
                                            .withStringLiteralArgument(jsid)
                                            .ofType("ComboBox");
                                });
                        iface.property(name)
                                .ofType("LabeledComponent<string>");
                        ifaceImpl.property(name)
                                .ofType("LabeledComponent<string>");
                        generateModelRawValueAssignment(required, modelGetter,
                                name, rawModelVariable, jsonName);
                        break;
                    case LIST:
                    case SET:

                    default:
                        bb.blankLine()
                                .lineComment("Unsupported type: " + target.getType()
                                        + " for " + target.getId());
                        continue;
                }

                generateValidationStanza(idPath, name, e.getKey(), e.getValue(), target,
                        validation, ifaceImpl, src);

                target.getTrait(DocumentationTrait.class)
                        .ifPresent(dox -> {
                            bb.invoke("setTooltip")
                                    .withStringLiteralArgument(dox.getValue())
                                    .on(name);
                        });
                localFieldForIfaceField.put(name, itemType);
                adder.add(name);
            } finally {
                idPath.pop();
            }
        }
    }

    public void generateModelRawValueAssignment(boolean required, TSGetterBlockBuilder<ClassBuilder<TypescriptSource>> modelGetter, String name, String rawModelVariable, String jsonName) {
        if (!required) {
            modelGetter.iff("!this." + name + ".isUnset()")
                    .statement(rawModelVariable + "['" + jsonName
                            + "'] = this." + name + ".rawValue()")
                    .endIf();
        } else {
            modelGetter.statement(rawModelVariable + "['" + jsonName
                    + "'] = this." + name + ".rawValue()");
        }
    }

    private void generateValidationStanza(LinkedList<String> l, String fieldName,
            String key, MemberShape value,
            Shape target, TsBlockBuilder<ClassBuilder<TypescriptSource>> bb,
            ClassBuilder<TypescriptSource> ifaceImpl, TypescriptSource src) {
        boolean required = value.getTrait(RequiredTrait.class).isPresent();
        if (!required) {
            ConditionalClauseBuilder<TsBlockBuilder<ClassBuilder<TypescriptSource>>> test
                    = bb.iff("'' != this." + fieldName + ".rawValue()");
            generateValidationStanzaImpl(l, fieldName, key, value, target, test, ifaceImpl, src);
            test.endIf();
        } else {
            bb.iff("this." + fieldName + ".isUnset()", ifBlock -> {
                ifBlock.invoke("validationConsumer")
                        .withStringLiteralArgument(jsIdFromStrings(l))
                        .withStringLiteralArgument("Required property is not set: " + key)
                        .inScope()
                        .invoke("withStyles")
                        .withStringLiteralArgument("err")
                        .onField(fieldName).ofThis()
                        .statement("result = false")
                        .endIf();
            });

            generateValidationStanzaImpl(l, fieldName, key, value, target, bb, ifaceImpl, src);
        }
    }

    private <B extends TSBlockBuilderBase<?, B>> void generateValidationStanzaImpl(
            LinkedList<String> l, String fieldName, String key, MemberShape value,
            Shape target, B bb, ClassBuilder<TypescriptSource> ifaceImpl,
            TypescriptSource src) {
        String valField = fieldName + "Value";

        bb.declare(valField)
                .ofType(jsTypeOf(target))
                .assignedToInvocationOf("value")
                .onField(fieldName).ofThis();

        String okField = fieldName + "OK";
        bb.declare(okField).assignedTo("true");

        switch (target.getType()) {
            case STRING:
                value.getMemberTrait(model, PatternTrait.class)
                        .ifPresent(pat -> {
                            bb.blankLine().lineComment("Validate " + key + " with " + pat);
                            bb.blankLine();

                            String pattern = camelCaseToDelimited(fieldName, '_')
                                    .replaceAll("_+", "_")
                                    .toUpperCase() + "_PATTERN";

//"pattern_" + capitalize(fieldName);
                            src.declareConst(pattern)
                                    .ofType("RegExp")
                                    .assignedToNew()
                                    .withStringLiteralArgument(pat.getValue())
                                    .ofType("RegExp");

                            bb.iff("!" + pattern + ".test(" + valField + ")",
                                    iff -> {
                                        iff.statement("result = false");
                                        iff.blankLine();
                                        iff.invoke("validationConsumer")
                                                .withStringLiteralArgument(jsIdFromStrings(l))
                                                .withStringLiteralArgument("Does not match the pattern /" + pat.getValue() + "/")
                                                .inScope();
                                        iff.statement(okField + " = " + false);

                                    });
                        });
                value.getMemberTrait(model, LengthTrait.class)
                        .ifPresent(len -> {
                            bb.blankLine().lineComment("Validate " + key + " with " + len);
                            len.getMin().ifPresent(min -> {
                                if (min == 0) {
                                    return;
                                }
                                bb.blankLine();
                                bb.iff(valField + ".length < " + min, iff -> {
                                    iff.statement("result = false");
                                    iff.blankLine();
                                    iff.invoke("validationConsumer")
                                            .withStringLiteralArgument(jsIdFromStrings(l))
                                            .withStringLiteralArgument("Length must be >= " + min)
                                            .inScope();
                                    iff.statement(okField + " = " + false);
                                });
                            });
                            len.getMax().ifPresent(max -> {
                                bb.iff(valField + ".length > " + max, iff -> {
                                    iff.statement("result = false");
                                    iff.blankLine();
                                    iff.invoke("validationConsumer")
                                            .withStringLiteralArgument(jsIdFromStrings(l))
                                            .withStringLiteralArgument("Length must be >= " + max)
                                            .inScope();
                                    iff.statement(okField + " = " + false);
                                });
                            });
                        });
                break;
            case INTEGER:
            case BYTE:
            case SHORT:
            case LONG:
            case BIG_INTEGER:
                value.getMemberTrait(model, RangeTrait.class)
                        .ifPresent(len -> {
                            len.getMin().ifPresent(min -> {
                                if (min.longValue() == 0) {
                                    return;
                                }
                                bb.blankLine();
                                bb.iff(valField + " < " + min, iff -> {
                                    iff.statement("result = false");
                                    iff.invoke("validationConsumer")
                                            .withStringLiteralArgument(jsIdFromStrings(l))
                                            .withStringLiteralArgument("Length must be >= " + min)
                                            .inScope();
                                    iff.statement(okField + " = " + false);
                                });

                            });
                            len.getMax().ifPresent(max -> {
                                bb.blankLine();
                                bb.iff(valField + " > " + max, iff -> {
                                    iff.statement("result = false");
                                    iff.invoke("validationConsumer")
                                            .withStringLiteralArgument(jsIdFromStrings(l))
                                            .withStringLiteralArgument("Length must be >= " + max)
                                            .inScope();
                                    iff.statement(okField + " = " + false);
                                });
                            });
                        });
        }
        bb.iff(okField)
                .invoke("withoutStyles")
                .withStringLiteralArgument("err")
                .onField(fieldName)
                .ofThis()
                .orElse()
                .invoke("withStyles")
                .withStringLiteralArgument("err")
                .onField(fieldName)
                .ofThis()
                .endIf();

    }

    private static class ComponentAdder implements AutoCloseable {

        private final String panel;
        private final int max;
        private final ContainerFactory rows;
        private String currRow;
        int addedToCurrentRow;
        private final TypescriptSource src;

        public ComponentAdder(String panel, int max, ContainerFactory rows,
                TypescriptSource src) {
            this.panel = panel;
            this.max = max;
            this.rows = rows;
            this.src = src;
        }

        void add(String compVar) {

            if (currRow == null || addedToCurrentRow++ >= max) {
                if (currRow != null) {
                    if (rows.block != null) {
                        rows.block.blankLine();
                        rows.block.invoke("addChild")
                                .withArgument(currRow)
                                .on(panel);

                    } else {
                        src.blankLine();
                        src.invoke("addChild")
                                .withArgument(currRow)
                                .on(panel);
                    }
                }
                addedToCurrentRow = 1;
                currRow = rows.next();
            }
            if (rows.block != null) {
                rows.block.blankLine();
                rows.block.invoke("addChild")
                        .withArgument(compVar)
                        .on(currRow);
            } else {
                src.blankLine();
                src.invoke("addChild")
                        .withArgument(compVar)
                        .on(currRow);
            }
        }

        @Override
        public void close() {
            if (addedToCurrentRow > 0 && currRow != null) {
                if (rows.block != null) {
                    rows.block.invoke("addChild")
                            .withArgument(currRow)
                            .on(panel);
                } else {
                    src.invoke("addChild")
                            .withArgument(currRow)
                            .on(panel);
                }
            }
            addedToCurrentRow = 0;
            currRow = null;
        }

    }

    private static class ContainerFactory {

        private final String prefix;
        private final String type;
        private final TypescriptSource src;
        private int item;
        private BiConsumer<String, OperationShape> listener;
        private TsBlockBuilder<?> block;

        public ContainerFactory(String prefix, String type, TypescriptSource src) {
            this.prefix = prefix;
            this.type = type;
            this.src = src;
        }

        public ContainerFactory onNewItem(BiConsumer<String, OperationShape> listener) {
            if (this.listener == null) {
                this.listener = listener;
            } else {
                this.listener = this.listener.andThen(listener);
            }
            return this;
        }

        public ContainerFactory withBlock(TsBlockBuilder<?> block, Runnable r) {
            TsBlockBuilder<?> old = this.block;
            this.block = block;
            try {
                r.run();
            } finally {
                this.block = old;
            }
            return this;
        }

        public String next() {
            String result = prefix + "_" + ++item;
            if (block != null) {
                block.declareConst(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);
            } else {
                src.declareConst(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);
            }
            if (listener != null) {
                listener.accept(result, null);
            }
            return result;
        }

        public String next(OperationShape sh) {
            String result = decapitalize(sh.getId().getName()) + "Panel";
            if (block != null) {
                block.declareConst(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);

            } else {
                src.declareConst(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);
            }
            if (listener != null) {
                listener.accept(result, sh);
            }
            return result;
        }

    }

}
