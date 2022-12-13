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
import com.telenav.smithy.ts.vogon.TypescriptSource;
import java.nio.file.Path;
import java.util.function.Consumer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.NumberShape;

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
                    .ofType(memberTypeName());
            cb.constructor(con -> {
                con.withArgument("value").ofType(memberTypeName())
                        .body(bb -> {
                            bb.statement("this.value = value");
                        });
            });
            cb.method("toString", mth -> {
                mth.returning("string", bb -> {
                    bb.returningInvocationOf("toString").on("this.value");
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
                bb.returningInvocationOf("toString")
                        .onField("value")
                        .ofThis();
            });
        });
    }

}
