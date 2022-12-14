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
package com.telenav.smithy.ts.generator;

import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.names.NumberKind;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.ConditionalClauseBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import static software.amazon.smithy.model.shapes.ShapeType.STRING;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public class ListGenerator extends AbstractTypescriptGenerator<ListShape> {

    final Shape memberTarget;

    public ListGenerator(ListShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        memberTarget = model.expectShape(shape.getMember().getTarget());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        boolean isSet = shape.isSetShape() || shape.getTrait(UniqueItemsTrait.class).isPresent();

        tb.declareClass(typeName(), cb -> {
            Shape target = model.expectShape(shape.getMember().getTarget());
            importShape(target, tb);
            boolean canBePrimitive = "smithy.api".equals(target.getId().getNamespace());
            String primitiveType = typeNameOf(target, false);
            String targetTypeName = canBePrimitive ? primitiveType : tsTypeName(target);
            String ext = isSet ? "Set<" + targetTypeName + ">" : "Array<" + targetTypeName + ">";
            cb.extending(ext);
            cb.exported();

            cb.constructor(con -> {
                con.withArgument("items").ofType(targetTypeName + "[]");
                con.body(bb -> {
                    if (canBePrimitive) {
                        // We can be called with a string where it should be string[],
                        // and if you create a new Set from a string, you get one
                        // element for each letter
                        if (memberTarget.getType() == STRING) {
                            bb.invoke("super")
                                    .withTernary("Array.isArray(items)")
                                    .expression("items")
                                    .expression("[items as string]")
                                    .inScope();
                        } else {
                            bb.invoke("super")
                                    .withArgument("items").inScope();
                        }
                    } else {
                        if (memberTarget.getType() == STRING) {
                            bb.invoke("super")
                                    .withTernary("Array.isArray(items)")
                                    .expression("items")
                                    .expression((isSet ? "" : "...") + "[items as string]")
                                    .inScope();
                        } else {
                            bb.invoke("super").withArgument(isSet ? "items" : "...items").inScope();
                        }
                    }
                });
            });

            cb.method("toString", mth -> {
                mth.makePublic().returning("string");
                mth.body(bb -> {
                    bb.declareConst("strings")
                            .ofType("string[]")
                            .assignedTo("[]");

                    bb.invoke("forEach")
                            .withLambda()
                            .withArgument("item").inferringType()
                            .body(lbb -> {
                                lbb.invoke("push")
                                        .withInvocationOf("toString")
                                        .on("item")
                                        .on("strings");
                            }).onThis();

                    bb.declareConst("result")
                            .ofType("string")
                            .assignedToInvocationOf("join")
                            .withStringLiteralArgument(",")
                            .on("strings");

                    bb.invoke("log")
                            .withStringLiteralArgument("Strings are ")
                            .withArgument("strings")
                            .on("console");

                    bb.invoke("log")
                            .withArgument("result")
                            .withArgument("this")
                            .on("console");

                    bb.returningInvocationOf("join")
                            .withStringLiteralArgument(",")
                            .on("strings");
                });
            });

            cb.method("fromJsonObject", mth -> {
                mth.makeStatic()
                        .withArgument("input").ofType("any");
                mth.returning(typeName(), bb -> {

                    bb.statement("let items : " + tsTypeName(target) + "[] = []");
                    TypeStrategy<?> strat = strategies.strategy(memberTarget);

                    ConditionalClauseBuilder<TsBlockBuilder<Void>> ia = bb.iff("Array.isArray(input)");

                    ia.declare("values")
                            .ofType("any[]")
                            .assignedTo().as("any[]")
                            .expression("input");

                    ia.invoke("forEach")
                            .withLambda()
                            .withArgument("item").ofType("any")
                            .body(lbb -> {
                                strat.instantiateFromRawJsonObject(lbb,
                                        TsPrimitiveTypes.ANY.variable("item"),
                                        "converted", true);
                                lbb.invoke("push").withArgument("converted").on("items");
                            }).on("values");

                    ConditionalClauseBuilder<TsBlockBuilder<Void>> els = ia.orElse("typeof input === 'string'");
                    els.declare("rex").ofType("RegExp")
                            .assignedToNew().withArgument("\\s*?,\\s*")
                            .ofType("RegExp");
                    els.declare("strings").ofType("string[]")
                            .assignedToInvocationOf("split")
                            .withArgument().as("string").expression("input");
                    NumberKind nk = NumberKind.forShape(target);
                    if (nk != null) {
                        els.invoke("forEach")
                                .withLambda()
                                .withArgument("val").ofType("string")
                                .withArgument("_index").ofType("number")
                                .body(lbb -> {
                                    TsPrimitiveTypes argType = TsPrimitiveTypes.NUMBER;
                                    lbb.lineComment("Do the thing! " + NumberKind.forShape(target));
                                    lbb.declareConst("parsed")
                                            .ofType("number")
                                            .assignedToInvocationOf(nk.jsParseMethod())
                                            .withArgument("val")
                                            .inScope();
                                    strat.instantiateFromRawJsonObject(lbb, argType.variable("parsed"), "converted", true);
                                    lbb.invoke("push")
                                            .withArgument("converted")
                                            .on("values");
                                });
                    } else {
                        els.invoke("forEach")
                                .withLambda()
                                .withArgument("val").ofType("string")
                                .withArgument("_index").ofType("number")
                                .body(lbb -> {
                                    strat.instantiateFromRawJsonObject(lbb, TsPrimitiveTypes.ANY.variable("val"), "converted", true);
                                    lbb.invoke("push")
                                            .withArgument("converted")
                                            .on("values");
                                });
                    }
                    els = els.orElse("typeof input !== 'undefined'");
                    els.lineComment("Fail over - see if the conversion method can do anything with it.");
                    strat.instantiateFromRawJsonObject(els, TsPrimitiveTypes.ANY.variable(
                            "input"), "converted", true);
                    els.invoke("push").withArgument("converted").on("items");
                    els.endIf();
                    bb.returningNew().withArgument("items").ofType(cb.name());
                });
            });
            if (canBePrimitive && !primitiveType.equals(targetTypeName)) {
                cb.method("convert", mth -> {
                    mth.withArgument("items").ofType(typeNameOf(target, false));
                    mth.returning(targetTypeName + "[]", bb -> {
                        bb.declareConst("result")
                                .ofType(targetTypeName + "[]")
                                .assignedTo("new " + targetTypeName + "[]");
                        bb.invoke("forEach")
                                .withLambda()
                                .withArgument("item").ofType(typeNameOf(target, false))
                                .body(lbb -> {
                                    lbb.invoke("push")
                                            .withArgument("new " + targetTypeName + "(item)")
                                            .on("result");
                                })
                                .on("items");
                    });
                });

                cb.constructor(con -> {
                    con.withArgument("items").ofType(typeNameOf(target, false));
                    con.body(bb -> {
                        bb.invoke("super")
                                .withInvocationOf("convert")
                                .on(cb.name())
                                .inScope();

                    });
                });

            }
            generateToJsonString(cb);
            generateToJson(cb);
            generateAddTo(cb);
        });

        c.accept(tb);
    }

    @Override
    protected void toJsonBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        Shape target = model.expectShape(shape.getMember().getTarget());
//        String primitiveType = typeNameOf(target, false);
        String primitiveType = jsTypeOf(target);
        String typeName = tsTypeName(target);

        bb.blankLine()
                .lineComment("Target " + target.getId())
                .lineComment("prim type " + primitiveType)
                .lineComment("tsTypeName of target " + typeName)
                .lineComment("jsTypeName of target " + jsTypeOf(target))
                .lineComment("jsTypeName of shape " + jsTypeOf(shape));

        bb.declare("objs")
                .ofType(primitiveType + "[]")
                .assignedTo("[]");
        bb.invoke("forEach")
                .withLambda(lb -> {
                    lb.withArgument("item").ofType(typeName);
                    lb.body(lbb -> {
                        if (typeName.equals(primitiveType)) {
                            lbb.lineComment("TN eq prim - simple push");
                            lbb.invoke("push")
                                    .withArgument("item")
                                    .on("objs");
                        } else {
                            lbb.lineComment("TN ne prim - use toJson");
                            lbb.invoke("push")
                                    .withInvocationOf(TO_JSON)
                                    .on("item")
                                    .on("objs");
                        }
                        lbb.endBlock();
                    });
                })
                .on("this");
        bb.returning("objs");
    }

}
