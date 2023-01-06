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
import software.amazon.smithy.model.shapes.StringShape;

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
            cb.exported();
            cb.property("value")
                    .setPublic()
                    .readonly()
                    .ofType("string");
            cb.exported().constructor(con -> {
                con.withArgument("value").ofType("string")
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

}
