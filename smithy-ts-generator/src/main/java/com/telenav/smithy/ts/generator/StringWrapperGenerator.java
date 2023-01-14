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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.GREATER_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.LESS_THAN;
import static com.telenav.smithy.ts.vogon.TypescriptSource.BinaryOperations.NOT_EQUALLING;
import com.telenav.smithy.ts.vogon.TypescriptSource.ClassBuilder;
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;

/**
 *
 * @author Tim Boudreau
 */
class StringWrapperGenerator extends AbstractTypescriptGenerator<StringShape> {

    StringWrapperGenerator(StringShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource tb = src();

        tb.declareClass(typeName(), cb -> {

            shape.getTrait(PatternTrait.class).ifPresent(pat -> {
                cb.property(strategy.patternFieldName()).setPublic().setStatic().readonly()
                        .initializedWithNew(nb -> nb.withStringLiteralArgument(pat.getValue()).ofType("RegExp"));
            });

            applyValidatableInterface(cb);
            cb.exported();
            cb.property("value")
                    .setPublic()
                    .readonly()
                    .ofType("string");

            cb.getter("length", get -> {
                get.returning().field("length").ofField("value").ofThis();
            });

            cb.exported().constructor(con -> {
                con.makePublic().withArgument("value").ofType("string")
                        .body(bb -> {
                            bb.statement("this.value = value");
                        });
            });
            cb.method("toString", mth -> {
                mth.returning("string", bb -> {
                    bb.statement("return this.value");
                });
            });
            generateToJson(cb);
            generateAddTo(cb);
            generateToJsonString(cb);
            TsBlockBuilder<ClassBuilder<Void>> fromJsonObject = cb.method(FROM_JSON)
                    .makePublic()
                    .withArgument("input").ofType("any")
                    .makeStatic()
                    .returning(cb.name());

            fromJsonObject.ifTypeOf("input", "string")
                    .returningNew()
                    .withArgument().as("string")
                    .expression("input")
                    .ofType(cb.name());

            fromJsonObject.ifDefined("input")
                    .returningNew()
                    .withInvocationOf("toString")
                    .on("input")
                    .ofType(cb.name());

            fromJsonObject.throwing(nb -> {
                nb.withStringLiteralArgument("Cannot create a " + cb.name() + " from undefined.");
            });
            fromJsonObject.endBlock();
        });

        c.accept(tb);
    }

    @Override
    public void generateToJsonString(TypescriptSource.ClassBuilder<?> cb) {
        cb.method(TO_JSON_STRING, mth -> {
            mth.returning("string", bb -> {
                bb.returningInvocationOf("stringify")
                        .withField("value")
                        .ofThis()
                        .on("JSON");
            });
        });
    }

    @Override
    protected <T, R> void generateValidationMethodBody(TsBlockBuilder<T> bb, ClassBuilder<R> cb) {
        strategy.validate("path", bb, "this.value", false);
        /*
        shape.getTrait(LengthTrait.class).ifPresent(len -> {
            len.getMin().ifPresent(min -> {
                if (min != 0L) {
                    boolean same = len.getMax().map(val -> val.equals(min)).orElse(false);
                    if (same) {
                        bb.iff().operation(NOT_EQUALLING).field("length").ofField("value").ofThis()
                                .literal(min)
                                .invoke("push")
                                .withStringLiteralArgument(shape.getId().getName() + " length must be exactly " + min)
                                .on("result")
                                .endIf();
                    } else {
                        bb.iff().operation(LESS_THAN).field("length").ofField("value").ofThis()
                                .literal(min)
                                .invoke("push")
                                .withStringLiteralArgument(shape.getId().getName() + " length must be >= " + min)
                                .on("result")
                                .endIf();
                    }
                }
            });
            len.getMax().ifPresent(max -> {
                boolean same = len.getMin().map(val -> val.equals(max)).orElse(false);
                if (!same && max < Integer.MAX_VALUE) {
                    bb.iff().operation(GREATER_THAN).field("length").ofField("value").ofThis()
                            .literal(max)
                            .invoke("push")
                            .withStringLiteralArgument(shape.getId().getName() + " length must be <= " + max)
                            .on("result")
                            .endIf();
                }
            });
        });
        shape.getTrait(PatternTrait.class).ifPresent(pat -> {
            if (!".*".equals(pat)) {
                bb.iff("!" + cb.name() + ".PATTERN.test(this.value)")
                        .invoke("push")
                        .withStringLiteralArgument(shape.getId().getName() + " must match the pattern "
                                + pat.getValue())
                        .on("result")
                        .endIf();
            }
        });
         */
    }
}
