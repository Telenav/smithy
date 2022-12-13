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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.FunctionBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.InterfaceBuilder;
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
import java.util.Set;
import java.util.TreeMap;
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

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = typescript("testit");

        importComponent("Row", src);
        importComponent("Panel", src);
        importComponent("NavPanel", src);

//        src.statement("type PanelAndModel<T> = [Panel, T]");
        String navVar = "mainNavigation";

        src.declare(navVar)
                .ofType("NavPanel")
                .assignedToNew()
                .withStringLiteralArgument("nav")
                .withStringLiteralArgument("content")
                .ofType("NavPanel");

        ContainerFactory panels = new ContainerFactory("panel", "Panel", src);
        ContainerFactory rows = new ContainerFactory("row", "Row", src);

        FunctionBuilder<TypescriptSource> createUI = src.function("createUI")
                .withArgument(navVar)
                .ofType("NavPanel");

        src.blankLine().invoke("createUI").withArgument(navVar).inScope();

        TsBlockBuilder<TypescriptSource> createUiBody = createUI.body();
        List<InterfaceBuilder<TypescriptSource>> ifaces = new ArrayList<>();
        List<Runnable> blocks = new ArrayList<>();

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

            Set<OperationShape> ops = ResourceGraphs.graph(model, shape)
                    .transformedClosure(shape, sh
                            -> sh.asOperationShape().orElse(null));

            Map<OperationShape, String> panelForOp = new HashMap<>();
//        src.generateDebugLogCode();
            for (OperationShape op : ops) {
                String pnl = generateUiCodeForOp(op, panels, rows, src, ifaces, blocks, createUiBody);
                panelForOp.put(op, pnl);
            }
        });

        src.invoke("attach")
                .withStringLiteralArgument("navholder")
                .on(navVar);

        createUiBody.endBlock();

        ifaces.forEach(InterfaceBuilder::close);
        blocks.forEach(blk -> blk.run());

        c.accept(src);
    }

    private String generateUiCodeForOp(OperationShape op, ContainerFactory panels,
            ContainerFactory rows, TypescriptSource bb,
            List<InterfaceBuilder<TypescriptSource>> ifaces,
            List<Runnable> blocks, TsBlockBuilder<TypescriptSource> createUiBody) {

        createUiBody.blankLine().blankLine().lineComment(op.getId().toString());
        String panel = panels.next(op);

        op.getInput().flatMap(inp -> model.expectShape(inp).asStructureShape())
                .ifPresent(input -> {
                    generateInputUIFor(panel, op, rows, input, bb, ifaces, blocks, createUiBody);
                });

        return panel;
    }

    private void generateInputUIFor(String panel, OperationShape op,
            ContainerFactory rows, StructureShape input, TypescriptSource bb,
            List<InterfaceBuilder<TypescriptSource>> ifaces,
            List<Runnable> blocks, TsBlockBuilder<TypescriptSource> createUiBody) {
//        String currRow = rows.next();

        String uiModelType = escape(op.getId().getName() + "UIModel");

        try (ComponentAdder add = new ComponentAdder(panel, 3, rows, bb)) {
            InterfaceBuilder<TypescriptSource> iface
                    = bb.declareInterface(escape(op.getId().getName() + "UIModel"));
            ifaces.add(iface);
            LinkedList<String> idPath = new LinkedList<>(
                    asList(input.getId().getName(), op.getId().getName()));

            FunctionBuilder<TypescriptSource> func
                    = bb.function("create" + capitalize(panel));
            func.withArgument(panel)
                    .ofType("Panel")
                    .returning(uiModelType);
            TsBlockBuilder<TypescriptSource> block = func.body();

            Map<String, String> localFieldForIfaceField = new TreeMap<>();

            rows.withBlock(block, () -> {
                drillThroughShapes(idPath,
                        add, input, bb, iface, block, localFieldForIfaceField);
                add.close();
            });

            createUiBody.declareConst(decapitalize(op.getId().getName()) + "ModelInstance")
                    .ofType(uiModelType)
                    .assignedToInvocationOf("create" + capitalize(panel))
                    .withArgument(panel)
                    .inScope();

            block.declareConst("__result")
                    .assignedToObjectLiteral(olb -> {
                        localFieldForIfaceField.forEach((name, type) -> {
                            olb.assigning(name).toExpression(name);
                        });
                    });

            blocks.add(() -> {
                block.returning("__result");
                bb.blankLine();
            });
        }
    }

    private String jsIdFromStrings(List<? extends String> l) {
        return Strings.join('.', l);
    }

    private String varNameFromStrings(List<? extends String> l) {
        return Strings.join('_', l);
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

    private void drillThroughShapes(LinkedList<String> idPath, ComponentAdder adder,
            StructureShape input, TypescriptSource src,
            InterfaceBuilder<TypescriptSource> iface, TsBlockBuilder<TypescriptSource> bb,
            Map<String, String> localFieldForIfaceField) {

        importComponent("LabeledComponent", src);
        for (Map.Entry<String, MemberShape> e : input.getAllMembers().entrySet()) {
            idPath.push(e.getKey());
            try {
                String name = varNameFromStrings(idPath);
                String jsid = jsIdFromStrings(idPath);
                Shape target = model.expectShape(e.getValue().getTarget());

                bb.blankLine().blankLine().lineComment("Shape " + target.getType() + " " + target.getId().getName())
                        .lineComment("For member " + e.getKey() + " of " + input.getId().getName())
                        .lineComment("Var " + name);

                String itemType;
                switch (target.getType()) {
                    case STRUCTURE:
                        drillThroughShapes(idPath, adder, target.asStructureShape().get(), src, iface, bb, localFieldForIfaceField);
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
                        break;
                    case BIG_INTEGER:
                    case INTEGER:
                    case LONG:
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
                        break;
                    default:
                        bb.blankLine()
                                .lineComment("Unsupported type: " + target.getType()
                                        + " for " + target.getId());
                        continue;
                }
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

            if (currRow == null || addedToCurrentRow++ > max) {
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
                block.declare(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);
            } else {
                src.declare(result)
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
                block.declare(result)
                        .ofType(type)
                        .assignedToNew().ofType(type);

            } else {
                src.declare(result)
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
