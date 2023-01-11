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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 *
 * @author Tim Boudreau
 */
class NumberWrapperGenerator extends AbstractTypescriptGenerator<NumberShape> {

    NumberWrapperGenerator(NumberShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    String memberTypeName() {
        return typeNameOf(shape, true);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        tb.declareClass(typeName(), cb -> {
            cb.exported();
            cb.property("value")
                    .setPublic()
                    .readonly()
                    .ofType(memberTypeName());
            cb.constructor(con -> {
                con.withArgument("value").ofType(memberTypeName())
                        .body(bb -> {
                            bb.assignField("value").ofThis().to("value");
                        });
            });
            cb.method("toString", mth -> {
                mth.returning("string", bb -> {
                    bb.returningInvocationOf("toString").onField("value").ofThis();
                });
            });
            generateToJson(cb);
            generateAddTo(cb);
            generateToJsonString(cb);

            cb.method("fromJsonObject", mth -> {
                mth.makePublic().makeStatic()
                        .withArgument("value").ofType("any");
                mth.body(bb -> {
                    bb.ifTypeOf("value", "number")
                            .returningNew().withArgument("value as number").ofType(typeName());

                    // Pending:  Probably we need real generators for big number types
                    // and strategy corresponding to them
                    NumberKind nk = NumberKind.forShape(shape);
                    if (nk == null) {
                        if (shape.getType() == ShapeType.BIG_DECIMAL) {
                            nk = NumberKind.DOUBLE;
                        } else {
                            nk = NumberKind.LONG;
                        }
                    }

                    bb.ifTypeOf("value", "string")
                            .returningNew()
                            .withInvocationOf(nk.jsParseMethod())
                            .withArgument().as("string").expression("value")
                            .inScope()
                            .ofType(typeName());

                    bb.ifTypeOf("value", "object")
                            .iff("value instanceof " + typeName())
                            .returning().as(typeName()).expression("value");

                    bb.throwing(thrown -> {
                        thrown.withStringConcatenation()
                                .append("Cannot convert ")
                                .appendExpression("value")
                                .append(" (")
                                .appendExpression("typeof value")
                                .append(") to a" + typeName())
                                .endConcatenation();
                    });
                });
            });
        });
        c.accept(tb);
    }

    @Override
    public void generateToJsonString(TypescriptSource.ClassBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.returning("string", bb -> {
                bb.returningInvocationOf("toString")
                        .onField("value")
                        .ofThis();
            });
        });
    }

}
