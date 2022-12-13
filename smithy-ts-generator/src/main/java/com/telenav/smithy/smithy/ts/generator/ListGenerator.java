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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

/**
 *
 * @author Tim Boudreau
 */
public class ListGenerator extends AbstractTypescriptGenerator<ListShape> {

    public ListGenerator(ListShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
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
                        bb.invoke("super").withArgument("items").inScope();
                    } else {
                        bb.invoke("super").withArgument("...items").inScope();
                    }
                });
            });
            cb.method("fromJsonObject", mth -> {
                mth.makeStatic()
                        .withArgument("input").ofType(jsTypeOf(shape));
                mth.returning(typeName(), bb -> {

                    bb.blankLine()
                            .lineComment("can be prim " + canBePrimitive)
                            .lineComment("primitive type " + primitiveType)
                            .lineComment("jsType " + jsTypeOf(shape))
                            .lineComment("jsType of shape " + jsTypeOf(target))
                            .lineComment("targetType " + targetTypeName)
                            .lineComment("isSet? " + isSet);

                    if (jsTypeOf(target).equals(targetTypeName)) {
                        bb.blankLine().lineComment("a");
                        bb.returningNew().withArgument("input").ofType(cb.name());
                    } else {
                        bb.blankLine().lineComment("b");
                        bb.declare("result")
                                .ofType("Array<" + targetTypeName + ">")
                                .assignedTo("[]");

                        bb.invoke("forEach")
                                .withLambda()
                                .withArgument("item").ofType("any")
                                .body(lbb -> {
                                    lbb.invoke("push").withArgumentFromInvoking("fromJsonObject")
                                            .withArgument("item")
                                            .on(targetTypeName)
                                            .on("result");
                                });
                        bb.returningNew().withArgument("result").ofType(cb.name());
                    }
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
                                .withArgumentFromInvoking("convert")
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
        String primitiveType = typeNameOf(target, false);
        String typeName = tsTypeName(target);

        bb.blankLine()
                .lineComment("Target " + target.getId())
                .lineComment("prim type " + primitiveType)
                .lineComment("tsTypeName of target " + typeName)
                .lineComment("jsTypeName of target " + jsTypeOf(target))
                .lineComment("jsTypeName of shape " + jsTypeOf(shape));

        if (typeName.equals(primitiveType)) {
            bb.returning("this");
            return;
        }
        bb.declare("objs")
                .ofType(primitiveType + "[]")
                .assignedTo("[]");
        bb.invoke("forEach")
                .withLambda(lb -> {
                    lb.withArgument("item").ofType(typeName);
                    lb.body(lbb -> {
                        lbb.invoke("push")
                                .withArgumentFromInvoking(TO_JSON)
                                .on("item")
                                .on("objs");
                        lbb.endBlock();
                    });
                })
                .on("this");
        bb.returning("objs");
    }

}
