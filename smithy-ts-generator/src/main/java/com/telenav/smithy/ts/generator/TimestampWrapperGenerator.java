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
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 *
 * @author Tim Boudreau
 */
public class TimestampWrapperGenerator extends AbstractTypescriptGenerator<TimestampShape> {

    public TimestampWrapperGenerator(TimestampShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();

        src.declareClass(tsTypeName(shape), cb -> {
            cb.extending("Date");
            shape.getTrait(DocumentationTrait.class)
                    .ifPresent(dox -> cb.docComment(dox.getValue()));
            cb.constructor(con -> {
                con.withArgument("value")
                        .optional()
                        .or()
                        .withType("number")
                        .withType("string")
                        .ofType("Date");
                con.body(bb -> {
                    bb.invoke("super")
                            .withTernary("typeof value === 'undefined'")
                            .invoke("getTime")
                            .onNew().ofType("Date")
                            .ternary("value instanceof Date")
                            .invoke("getTime")
                            .on("value as Date")
                            .ternary("typeof value === 'number'")
                            .expression("value as number")
                            .invoke("parse")
                            .withArgument("value as number")
                            .on("Date")
                            .inScope();
                });
            });
            cb.method("toJSON", mth -> {
                mth.makePublic().returning("string")
                        .returningInvocationOf("toISOString")
                        .onThis();
            });
            cb.method("toString", mth -> {
                mth.makePublic().returning("string")
                        .returningInvocationOf("toISOString")
                        .onThis();
            });

            generateToJsonString(cb);
            generateAddTo(cb);
        });

        c.accept(src);
    }

}
