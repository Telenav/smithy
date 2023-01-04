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
import com.telenav.smithy.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;

/**
 *
 * @author Tim Boudreau
 */
final class MapGenerator extends AbstractTypescriptGenerator<MapShape> {

    private final MemberShape keyMember;
    private final MemberShape valMember;
    private final Shape keyShape;
    private final Shape valShape;
    private final String keyType;
    private final String valType;
    private final String rawKeyType;
    private final String rawValueType;
    private final String supertype;
    private final boolean keyPrimitive;
    private final boolean valPrimitive;

    public MapGenerator(MapShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        keyMember = shape.getKey();
        valMember = shape.getValue();
        keyShape = model.expectShape(keyMember.getTarget());
        valShape = model.expectShape(valMember.getTarget());
        keyType = tsTypeName(keyShape);
        valType = tsTypeName(valShape);
        rawKeyType = typeNameOf(keyShape, true);
        rawValueType = typeNameOf(valShape, true);
        supertype = "Map<" + keyType + ", " + valType + ">";
        keyPrimitive = "smithy.api".equals(keyShape.getId().getNamespace());
        valPrimitive = "smithy.api".equals(keyShape.getId().getNamespace());
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();

        src.declareClass(tsTypeName(shape), cb -> {
            cb.extending(supertype);

            cb.constructor(con -> {
                con.withArgument("orig")
                        .optional()
                        .ofType(supertype);
                con.body(bb -> {
                    bb.invoke("super").inScope();

                    bb.blankLine();
                    bb.lineComment("Key Shape " + keyShape.getId());
                    bb.lineComment("Val Shape " + valShape.getId());
                    bb.lineComment("Key Type " + keyShape.getType());
                    bb.lineComment("Val Type " + valShape.getType());
                    bb.lineComment("Key prim " + keyPrimitive);
                    bb.lineComment("Val prim " + valPrimitive);
                    System.out.println("Key Type " + keyType);
                    System.out.println("Val Type " + valType);
                    System.out.println("Raw Key Type " + rawKeyType);
                    System.out.println("Raw Val Type " + rawValueType);
                    bb.lineComment("Key Type TS " + tsTypeName(keyShape));
                    bb.lineComment("Val Type TS " + tsTypeName(valShape));

                    bb.blankLine();

                    bb.iff("orig", iff -> {
                        iff.invoke("forEach")
                                .withLambda()
                                .withArgument("v").ofType(valType)
                                .withArgument("k").ofType(keyType)
                                .body(lbb -> {
                                    lbb.invoke("set")
                                            .withArgument("k")
                                            .withArgument("v")
                                            .onThis();
                                }).on("(orig as " + supertype + ")");
                    });
                });
            });

            generateAddTo(cb);
            generateToJsonString(cb);
            generateToJson(cb);
            generateFromJson(cb);
        });

        c.accept(src);
    }

    @Override
    protected void toJsonBody(TypescriptSource.TsBlockBuilder<Void> bb) {
        bb.declareConst("result").ofType("object").assignedTo("{}");
        bb.invoke("forEach")
                .withLambda()
                .withArgument("v").ofType(valType)
                .withArgument("k").ofType(keyType)
                .body(lbb -> {
                    Assignment<TsBlockBuilder<Void>> assig;
                    if (keyPrimitive) {
                        assig = lbb.assignRawProperty("k")
                                .of("result");
                    } else {
                        assig = lbb.assignRawProperty().invoke("toString").on("k").of("result");
                    }
                    if (valPrimitive) {
                        assig.assignedTo("v");
                    } else {
                        switch (valShape.getType()) {
                            case STRUCTURE:
                            case LIST:
                            case MAP:
                            case SET:
                                assig.assignedToInvocationOf("toJSON").on("v");
                                break;
                            default:
                                assig.assignedToInvocationOf("toString").on("v");
                        }
                    }
                }).onThis();
    }

    private void generateFromJson(TypescriptSource.ClassBuilder<Void> cb) {
        TypeStrategy keyStrategy = strategy(keyShape);
        TypeStrategy valStrategy = strategy(valShape);
        cb.method("fromJsonObject", mth -> {
            mth.docComment("Create a `" + cb.name() + "` from an ad-hoc object returned by JSON.parse().");
            mth.makePublic().makeStatic().withArgument("obj").ofType("any")
                    .returning(cb.name());
            mth.body(bb -> {
                bb.declareConst("result").ofType(cb.name())
                        .assignedToNew().ofType(cb.name());
                bb.forVar("k", loop -> {
                    keyStrategy.instantiateFromRawJsonObject(loop,
                            TsPrimitiveTypes.STRING.variable("k"), "key", true);
                    valStrategy.instantiateFromRawJsonObject(loop,
                            valStrategy.rawVarType().variable("obj[k]"), "value", true);
                    loop.invoke("set")
                            .withArgument("key")
                            .withArgument("value")
                            .on("result");
                    loop.over("obj");
                });
                bb.returning("result");
            });
        });
    }

}
