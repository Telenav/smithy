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
import software.amazon.smithy.model.shapes.DocumentShape;

/**
 *
 * @author Tim Boudreau
 */
final class DocumentGenerator extends AbstractTypescriptGenerator<DocumentShape> {

    DocumentGenerator(DocumentShape shape, Model model, LanguageWithVersion ver, Path dest, GenerationTarget target) {
        super(shape, model, ver, dest, target);
    }

    @Override
    public void generate(Consumer<TypescriptSource> c) {
        TypescriptSource src = src();
        
        src.declareClass(tsTypeName(shape), cb -> {
            cb.extending("Map<string, any>");
            cb.constructor(con -> {
                con.makePublic().withArgument("obj").ofType("any");
                con.body(bb -> {
                    bb.invoke("forEach")
                            .withLambda()
                            .withArgument("v").ofType("any")
                            .withArgument("k").ofType("string")
                            .body(lbb -> {
                                lbb.invoke("set")
                                        .withArgument("k")
                                        .withArgument("v")
                                        .onThis();
                            });
                });
            });
            generateToJson(cb);
            generateToJsonString(cb);
            generateAddTo(cb);
            cb.method("fromJsonObject", mth -> {
                mth.makePublic().withArgument("obj").ofType("any");
                mth.body(bb -> {
                    bb.returningNew().withArgument("obj").ofType(cb.name());
                });
            });
        });
        c.accept(src);
    }

    @Override
    protected void addContentsToJsonObject(String nameVar, String targetVar, TsBlockBuilder<Void> bb) {
        bb.assignElement().literal(nameVar).of(targetVar)
                .assignedToInvocationOf("toJSON").on("this");
    }
    
    

}
