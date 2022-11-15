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
package com.mastfrog.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.smithy.generators.GenerationTarget;
import com.mastfrog.smithy.generators.LanguageWithVersion;
import static com.mastfrog.smithy.java.generators.builtin.AbstractNumberGenerator.VALUE_FIELD;
import java.nio.file.Path;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShortShape;

/**
 *
 * @author Tim Boudreau
 */
final class ShortModelGenerator extends AbstractNumberGenerator<ShortShape> {

    ShortModelGenerator(ShortShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected <T, R> void generateHashCodeComputation(ClassBuilder<T> cb,
            ClassBuilder.BlockBuilder<R> bb) {
        int p = (int) prime(cb.fqn());
        bb.returning(ClassBuilder.variable(VALUE_FIELD).times(p).toString());
    }

    @Override
    protected void generateAlternateConstructors(ClassBuilder<String> cb) {
        cb.method("__checkIntValue")
                .withModifier(PRIVATE, STATIC)
                .addArgument("int", "intValue")
                .returning("short")
                .body(bb -> {
                    bb.lineComment("Ensure we cannot be passed an invalid int value");
                    bb.lineComment("in the convenience constructor.");
                    IfBuilder<?> ifb = bb.iff().booleanExpression("intValue < Short.MIN_VALUE"
                            + " || intValue > Short.MAX_VALUE");
                    validationExceptions().createThrow(cb, ifb, "Value outside the bounds of Short: ", "intValue");
                    ifb.endIf();
                    bb.returning("(short) intValue");
                });

        cb.constructor(con -> {
            con.setModifier(PUBLIC)
                    .addArgument("int", "intValue")
                    .docComment("Convenience constructor which takes an int."
                            + "\n@param intValue an integer value, which must fit within the range this class accepts\n"
                            + "@throws " + validationExceptions().name() + " if the value is out of range")
                    .body(bb -> {
                        bb.invoke("this")
                                .withArgumentFromInvoking("__checkIntValue")
                                .withArgument("intValue").inScope()
                                .inScope();
                    });
        });
    }

}
