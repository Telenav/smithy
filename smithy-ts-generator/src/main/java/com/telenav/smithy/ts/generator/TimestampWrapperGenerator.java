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
