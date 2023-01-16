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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import com.telenav.smithy.ts.vogon.TypescriptSource.Assignment;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
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
    private final MemberStrategy<?> keyStrategy;
    private final MemberStrategy<?> valStrategy;

    public MapGenerator(MapShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
        keyMember = shape.getKey();
        valMember = shape.getValue();
        keyShape = model.expectShape(keyMember.getTarget());
        valShape = model.expectShape(valMember.getTarget());
        keyType = tsTypeName(keyShape);
        valType = tsTypeName(valShape);
        keyStrategy = strategies.memberStrategy(keyMember, keyShape);
        valStrategy = strategies.memberStrategy(valMember, valShape);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();

        src.declareClass(tsTypeName(shape), cb -> {
            cb.extending("Map", pt -> pt.withTypeParameter(keyType).withTypeParameter(valType));
            applyValidatableInterface(cb);

            keyStrategy.declareValidationConstants(cb);
            valStrategy.declareValidationConstants(cb);
            
            cb.getter("length", get -> {
                get.returning().field("length").ofField("entries").ofThis();
            });
            
            cb.exported().constructor(con -> {
                con.makePublic().withArgument("orig")
                        .optional()
                        .ofType("Map", pt -> pt.withTypeParameter(keyType).withTypeParameter(valType));
                con.body(bb -> {
                    bb.invoke("super").inScope();

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
                                }).on("orig");// "(orig as Map<" + keyType + ", " + valType + ">)");
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
                }).onThis();
        bb.returning("result");
    }

    private void generateFromJson(TypescriptSource.ClassBuilder<Void> cb) {
        cb.method(FROM_JSON, mth -> {
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

    @Override
    protected <T, R> void generateValidationMethodHeadAndBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        bb.lineComment("Can implement validating " + canImplementValidating());
        bb.lineComment("Has validatable  " + hasValidatableValues());
        super.generateValidationMethodHeadAndBody(bb, cb);
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        strategy.validate("path", bb, "this", false);
    }
}
