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
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
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

    NumberWrapperGenerator(NumberShape shape, Model model, LanguageWithVersion ver,
            Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    String memberTypeName() {
        return typeNameOf(shape, true);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        tb.declareClass(typeName(), cb -> {
            applyValidatableInterface(cb);
            cb.implementing("Number");
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
            generateNumberImplementation(cb);
            generateToString(cb);
            generateToJson(cb);
            generateAddTo(cb);
            generateToJsonString(cb);
            generateFromJSON(cb);
        });
        c.accept(tb);
    }

    private void generateFromJSON(ClassBuilder<Void> cb) {
        cb.method(FROM_JSON, mth -> {
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
    }

    private void generateToString(ClassBuilder<Void> cb) {
        cb.method("toString", mth -> {
            mth.makePublic().returning("string", bb -> {
                bb.returningInvocationOf("toString").onField("value").ofThis();
            });
        });
    }

    @Override
    public void generateToJsonString(ClassBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.makePublic().returning("string", bb -> {
                bb.returningInvocationOf("toString")
                        .onField("value")
                        .ofThis();
            });
        });
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        strategy.validate("path", bb, "this.value", false);
    }

    private void generateNumberImplementation(ClassBuilder<Void> cb) {
        cb.method("toLocaleString", mth -> {
            mth.withArgument("locales").optional()
                    .ofType("Intl.LocalesArgument")
                    .withArgument("options").optional()
                    .ofType("Intl.NumberFormatOptions");
            TsBlockBuilder<Void> bb = mth.returning("string");
            bb.returningInvocationOf("toLocaleString")
                    .withArgument("locales")
                    .withArgument("options")
                    .onField("value").ofThis();
        });
        
        cb.method("valueOf").returning("number").returningField("value").ofThis();
        
        cb.method("toPrecision", mth -> {
            mth.withArgument("precision").optional().ofType("number")
                    .returning("string")
                    .returningInvocationOf("toPrecision")
                    .withArgument("precision")
                    .onField("value")
                    .ofThis();
        });

        cb.method("toExponential", mth -> {
            mth.withArgument("fractionDigits").optional().ofType("number")
                    .returning("string")
                    .returningInvocationOf("toExponential")
                    .withArgument("fractionDigits")
                    .onField("value")
                    .ofThis();
        });

        cb.method("toFixed", mth -> {
            mth.withArgument("fractionDigits").optional().ofType("number")
                    .returning("string")
                    .returningInvocationOf("toFixed")
                    .withArgument("fractionDigits")
                    .onField("value")
                    .ofThis();
        });

        /*
    toLocaleString(locales?: Intl.LocalesArgument, options?: Intl.NumberFormatOptions | undefined): string {
        return this.value.toLocaleString(locales, options);
    }

    valueOf(): number {
        return this.value;
    }
    toPrecision(precision?: number | undefined): string {
        return this.value.toPrecision(precision);
    }
    toExponential(fractionDigits?: number | undefined): string {
        return this.value.toExponential(fractionDigits);
    }
    toFixed(fractionDigits?: number | undefined): string {
        return this.value.toFixed(fractionDigits);
    }
        
         */
    }
}
