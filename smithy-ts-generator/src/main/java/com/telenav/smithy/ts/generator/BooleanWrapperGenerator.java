/*
 * Copyright 2023 Mastfrog Technologies.
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
import com.telenav.smithy.ts.vogon.TypescriptSource.TsBlockBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.traits.DocumentationTrait;

/**
 * Boolean wrapper types are more than a little silly, but we need to support
 * them consistently with everything else.
 *
 * @author Tim Boudreau
 */
final class BooleanWrapperGenerator extends AbstractTypescriptGenerator<BooleanShape> {

    BooleanWrapperGenerator(BooleanShape shape, Model model,
            LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();
        src.declareClass(tsTypeName(shape), cb -> {
            shape.getTrait(DocumentationTrait.class).ifPresent(dox -> {
                cb.docComment(dox.getValue());
            });
            cb.property("value").readonly().setPublic().ofType("boolean");
            cb.constructor(con -> {
                con.withArgument("value").ofType("boolean");
                con.makePublic();
                con.body(bb -> {
                    bb.assignField("value").ofThis().to("value");
                });
            });
            generateAddTo(cb);
            generateToJson(cb);
            generateToJsonString(cb);
            cb.method(FROM_JSON, mth -> {
                mth.withArgument("obj").ofType("any");
                mth.makePublic().makeStatic().returning(cb.name());
                mth.body(bb -> {
                    bb.returningNew()
                            .withArgument("true == obj")
                            .ofType(tsTypeName(shape));
                });
            });
            cb.method("toString")
                    .makePublic()
                    .returning("string")
                    .returning().ternary("this.value")
                    .literal("true").literal("false");
        });
        c.accept(src);
    }

    @Override
    protected void addContentsToJsonObject(String nameVar, String targetVar,
            TsBlockBuilder<Void> bb) {
        bb.assignElement().expression(nameVar).of(targetVar)
                .assignedToField("value").ofThis();
    }

}
