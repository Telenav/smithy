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
import com.telenav.smithy.ts.generator.type.MemberStrategy;
import com.telenav.smithy.ts.generator.type.TsPrimitiveTypes;
import com.telenav.smithy.ts.generator.type.TypeStrategy;
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ElementExpression;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;

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
            cb.extending("Map", pt -> pt.withTypeParameter(keyType).withTypeParameter(valType));

            cb.exported().constructor(con -> {
                con.makePublic().withArgument("orig")
                        .optional()
                        .ofType("Map", pt -> pt.withTypeParameter(keyType).withTypeParameter(valType));
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
            cb.method("toString")
                    .makePublic()
                    .returning("string")
                    .returningInvocationOf(TO_JSON_STRING).onThis();
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
                    MemberStrategy<?> keyStrat = strategies.memberStrategy(keyMember);
                    
                    ElementExpression<Assignment<TsBlockBuilder<Void>>> exp;
                    if (!keyStrat.isModelDefined() && keyStrat.shape().getType() == ShapeType.STRING) {
                        exp = lbb.assignElement().expression("k");
                    } else {
                        exp = lbb.assignElement().invoke("toString").on("k");
                    }
                    
                    
                    MemberStrategy<?> valStrat = strategies.memberStrategy(valMember);
                    valStrat.convertToRawJsonObject(lbb, 
                            valStrat.rawVarType().variable("v"), "value", true);
                    
                    exp.of("result").assignedTo("value");
                    
//                    Assignment<TsBlockBuilder<Void>> assig;
//                    if (keyPrimitive) {
//                        assig = lbb.assignRawProperty("k")
//                                .of("result");
//                    } else {
//                        assig = lbb.assignRawProperty().invoke("toString").on("k").of("result");
//                    }
//                    if (valPrimitive) {
//                        assig.assignedTo("v");
//                    } else {
//                        
//                        
//                        switch (valShape.getType()) {
//                            case STRUCTURE:
//                            case LIST:
//                            case MAP:
//                            case SET:
//                                assig.assignedToInvocationOf("toJSON").on("v");
//                                break;
//                            default:
//                                assig.assignedToInvocationOf("toString").on("v");
//                        }
//                    }
                }).onThis();
    }

    private void generateFromJson(TypescriptSource.ClassBuilder<Void> cb) {
        TypeStrategy<?> keyStrategy = strategy(keyShape);
        TypeStrategy<?> valStrategy = strategy(valShape);
        cb.method("fromJsonObject", mth -> {
            mth.docComment("Create a `" + cb.name() + "` from an ad-hoc object returned by JSON.parse().");
            mth.makePublic().makeStatic().withArgument("obj").ofType("any")
                    .returning(cb.name());
            mth.body(bb -> {
                bb.declareConst("result").ofType(cb.name())
                        .assignedToNew().ofType(cb.name());
                bb.forVar("k", loop -> {
                    keyStrategy.instantiateFromRawJsonObject(loop,
                            TsPrimitiveTypes.STRING.variable("k"), "key", true, true);
                    valStrategy.instantiateFromRawJsonObject(loop,
                            valStrategy.rawVarType().variable("obj[k]"), "value", true, true);
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
