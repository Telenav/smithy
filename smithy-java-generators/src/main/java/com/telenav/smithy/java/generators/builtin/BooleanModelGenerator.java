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
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.util.strings.Strings.decapitalize;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BooleanShape;

/**
 * Not sure there is any utility to wrapping a boolean in a type, but for
 * completeness...
 *
 * @author Tim Boudreau
 */
final class BooleanModelGenerator extends AbstractJavaGenerator<BooleanShape> {

    BooleanModelGenerator(BooleanShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected final ClassBuilder<String> classHead() {
        ClassBuilder<String> cb = super.classHead();
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator"
        );
        return cb;
    }

    @Override
    protected String additionalDocumentation() {
        return "This class is a wrapper around a primitive <code>boolean</code>.";
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = classHead();
        applyDocumentation(cb);

        Supplier s;

        cb.importing("java.util.function.BooleanSupplier",
                "java.util.function.Supplier")
                .implementing("BooleanSupplier", "Supplier<Boolean>");

        cb.field("value").withModifier(PRIVATE, FINAL)
                .ofType("boolean");

        generateStaticInstanceFields(cb);
        generateConstructor(cb);
        generateFactoryMethodWithStaticInstances(cb);
        generateGetAsBoolean(cb);
        generateSupplierImplementation(cb);
        generateHashCode(cb);
        generateToString(cb);
        generateConditionalMethods(cb);
        generateEquals(cb);
        sizes().addFields(shape, cb);
        addTo.accept(cb);
    }

    private void generateSupplierImplementation(ClassBuilder<String> cb) {
        cb.overridePublic("get").returning("Boolean").bodyReturning("value");
    }

    private void generateConditionalMethods(ClassBuilder<String> cb) {
        cb.method("ifTrue", mth -> mth.withModifier(PUBLIC)
                .docComment("Run the passed runnable if the value "
                        + "of this instance is <i>true</i>."
                        + "\n@param runIfTrue A runnable"
                        + "\n@return Whether or not the runnable was run")
                .addArgument("Runnable", "runIfTrue")
                .returning("boolean")
                .body(bb -> {
                    bb.iff().booleanExpression("value")
                            .invoke("run")
                            .on("runIfTrue")
                            .endIf();
                    bb.returning("value");
                }));

        cb.method("ifFalse", mth -> mth.withModifier(PUBLIC)
                .docComment("Run the passed runnable if the "
                        + "value of this instance is <i>false</i>."
                        + "\n@param runIfFalse A runnable"
                        + "\n@return Whether or not the runnable was run")
                .addArgument("Runnable", "runIfFalse")
                .returning("boolean")
                .body(bb -> {
                    bb.iff().booleanExpression("!value")
                            .invoke("run")
                            .on("runIfFalse")
                            .endIf();
                    bb.returning("!value");
                }));
    }

    private void generateToString(ClassBuilder<String> cb) {
        cb.overridePublic("toString",
                mth -> mth.returning("String")
                        .body(bb -> bb.returningInvocationOf("toString")
                        .withArgument("value")
                        .on("Boolean")));
    }

    @Override
    protected void generateHashCode(ClassBuilder<String> cb) {
        cb.overridePublic("hashCode", mth -> mth.returning("int")
                .body(bb -> bb.returningValue()
                .ternary()
                .booleanExpression("value")
                .literal(-1)
                .literal(0)));
    }

    private void generateStaticInstanceFields(ClassBuilder<String> cb) {
        cb.field("TRUE").withModifier(PUBLIC, STATIC, FINAL)
                .initializedWithNew(
                        nb -> nb.withArgument(true)
                                .ofType(cb.className()))
                .ofType(cb.className());

        cb.field("FALSE").withModifier(PUBLIC, STATIC, FINAL)
                .initializedWithNew(
                        nb -> nb.withArgument(false)
                                .ofType(cb.className()))
                .ofType(cb.className());
    }

    private void generateGetAsBoolean(ClassBuilder<String> cb) {
        cb.overridePublic("getAsBoolean",
                mth -> mth.returning("boolean")
                        .annotatedWith("JsonValue").closeAnnotation()
                        .docComment("Get the boolean value of this object.\n"
                                + "@return the boolean value")
                        .bodyReturning("value"));
    }

    private void generateFactoryMethodWithStaticInstances(ClassBuilder<String> cb) {
        cb.method(decapitalize(cb.className()), mth -> {
            mth.annotatedWith("JsonCreator").closeAnnotation()
                    .docComment("Factory method for instances of " + cb.className()
                            + ".\n@param value The boolean value for the instance"
                            + "\n@return a " + cb.className())
                    .withModifier(PUBLIC, STATIC)
                    .addArgument("boolean", "value")
                    .returning(cb.className())
                    .body(bb -> {
                        bb.returningValue()
                                .ternary().booleanExpression("value")
                                .expression("TRUE")
                                .expression("FALSE");
                    });
        });
    }

    private void generateConstructor(ClassBuilder<String> cb) {
        cb.constructor(con -> {
            con.addArgument("boolean", "value")
                    .setModifier(PRIVATE)
                    .body(bb -> {
                        bb.assign("this.value")
                                .toExpression("value");
                    });
        });
    }

    @Override
    protected <T, R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void generateEqualsComparison(String other,
            ClassBuilder<?> cb, B bb) {
        bb.returning(other + ".value == this.value");
    }

}
