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
package com.telenav.smithy.java.generators.builtin;

import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;

import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ByteShape;

/**
 *
 * @author Tim Boudreau
 */
final class ByteModelGenerator extends AbstractNumberGenerator<ByteShape> {

    ByteModelGenerator(ByteShape shape, Model model, Path destSourceRoot, GenerationTarget target, LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected <T, R> void generateHashCodeComputation(ClassBuilder<T> cb,
            ClassBuilder.BlockBuilder<R> bb) {
        int p = (int) prime(cb.fqn());
        bb.returning(variable(VALUE_FIELD).times(p).toString());
    }

    @Override
    protected void generateAlternateConstructors(ClassBuilder<String> cb) {
        cb.method("__checkIntValue")
                .withModifier(PRIVATE, STATIC)
                .addArgument("int", "intValue")
                .returning("byte")
                .body(bb -> {
                    bb.lineComment("Ensure we cannot be passed an invalid int value");
                    bb.lineComment("in the convenience constructor.");
                    IfBuilder<?> ifb = bb.iff().booleanExpression("intValue < Byte.MIN_VALUE"
                            + " || intValue > Byte.MAX_VALUE");
                    validationExceptions().createThrow(cb, ifb, "Value outside the bounds of Byte: ", "intValue");
                    ifb.endIf();
                    bb.returning("(byte) intValue");
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
