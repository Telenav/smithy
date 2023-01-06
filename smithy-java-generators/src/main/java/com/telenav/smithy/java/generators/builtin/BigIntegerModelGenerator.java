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
import com.telenav.smithy.generators.GenerationTarget;
import com.telenav.smithy.generators.LanguageWithVersion;
import com.telenav.smithy.java.generators.base.AbstractJavaGenerator;
import static com.mastfrog.util.strings.Strings.decapitalize;
import static com.telenav.smithy.validation.ValidationExceptionProvider.validationExceptions;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.traits.RangeTrait;

/**
 *
 * @author Tim Boudreau
 */
final class BigIntegerModelGenerator extends AbstractJavaGenerator<BigIntegerShape> {

    BigIntegerModelGenerator(BigIntegerShape shape, Model model, Path destSourceRoot, GenerationTarget target,
            LanguageWithVersion language) {
        super(shape, model, destSourceRoot, target, language);
    }

    @Override
    protected final ClassBuilder<String> classHead() {
        ClassBuilder<String> cb = super.classHead();
        cb.importing(
                "com.fasterxml.jackson.annotation.JsonValue",
                "com.fasterxml.jackson.annotation.JsonCreator",
                "java.math.BigInteger"
        );
        return cb;
    }

    @Override
    protected void generate(Consumer<ClassBuilder<String>> addTo) {
        ClassBuilder<String> cb = classHead();
        cb.importing(Supplier.class)
                .implementing("Supplier<BigInteger>", "Comparable<" + cb.className() + ">")
                .extending("Number");

        cb.field("value", fld -> {
            fld.withModifier(PRIVATE, FINAL)
                    .ofType("BigInteger");
        });

        generateRangeFieldsIfShapeHasRangeTrait(cb);

        generateConstructor(cb);

        generateLongConstructor(cb);

        generateSupplierImplementation(cb);

        cb.overridePublic("hashCode",
                hc -> hc.returning("int")
                        .body().returningInvocationOf("hashCode")
                        .on("value").endBlock());

        cb.overridePublic("toString",
                ts -> ts.returning("String")
                        .body().returningInvocationOf("toString")
                        .on("value").endBlock());

        cb.overridePublic("compareTo",
                mth -> mth.returning("int")
                        .addArgument(cb.className(), "other")
                        .body(
                                bb -> bb.returningInvocationOf("compareTo")
                                        .withArgumentFromField("value")
                                        .of("other")
                                        .on("value")
                        ));

        cb.method(decapitalize(cb.className()),
                mth -> mth.addArgument("BigInteger", "value")
                        .docComment("Convenience factory method.\n"
                                + "@param value a value\n"
                                + "@return a " + cb.className())
                        .withModifier(PUBLIC, STATIC)
                        .returning(cb.className())
                        .body(
                                bb -> bb.returningNew(
                                        nb -> nb.withArgument("value")
                                                .ofType(cb.className()))));
        generateEquals(cb);

        generateNumberDelegationMethod("byteValue", "byte", cb, true);
        generateNumberDelegationMethod("shortValue", "short", cb, true);
        generateNumberDelegationMethod("intValue", "int", cb, true);
        generateNumberDelegationMethod("longValue", "long", cb, true);
        generateNumberDelegationMethod("doubleValue", "double", cb, true);
        generateNumberDelegationMethod("floatValue", "float", cb, true);

        generateNumberDelegationMethod("byteValueExact", "byte", cb, false);
        generateNumberDelegationMethod("shortValueExact", "short", cb, false);
        generateNumberDelegationMethod("intValueExact", "int", cb, false);
        generateNumberDelegationMethod("longValueExact", "long", cb, false);
        sizes().addFields(shape, cb);
        addTo.accept(cb);
    }

    private void generateNumberDelegationMethod(String name, String type, ClassBuilder<String> cb,
            boolean isOverride) {
        cb.method(name, mth -> {
            if (isOverride) {
                mth.annotatedWith("Override").closeAnnotation();
            }
            mth.withModifier(PUBLIC)
                    .docComment("Delegates to the <code>" + name + "</code> method on"
                            + " the underlying BigInteger.\n@return a " + type)
                    .returning(type)
                    .body(bb -> {
                        bb.returningInvocationOf(name)
                                .onField("value")
                                .ofThis();
                    });

        });
    }

    public void generateSupplierImplementation(ClassBuilder<String> cb) {
        cb.overridePublic("get",
                mth -> mth.returning("BigInteger")
                        .annotatedWith("JsonValue")
                        .closeAnnotation()
                        .withModifier(PUBLIC)
                        .docComment("Get the BigInteger value of this object."
                                + "\n@return The value")
                        .bodyReturning("value"));
    }

    public void generateLongConstructor(ClassBuilder<String> cb) {
        cb.constructor(
                con -> con.setModifier(PUBLIC)
                        .addArgument("long", "value")
                        .docComment("Convenience constructor to create a new "
                                + cb.className() + " from a long.\n"
                                + "@param value A double")
                        .body(
                                bb -> bb.invoke("this")
                                        .withArgumentFromInvoking("valueOf")
                                        .withArgument("value")
                                        .on("BigInteger")
                                        .inScope()));
    }

    public void generateConstructor(ClassBuilder<String> cb) {
        cb.constructor(con -> con.setModifier(PUBLIC)
                .annotatedWith("JsonCreator").closeAnnotation()
                .addArgument("BigInteger", "value")
                .docComment("Creates a new " + cb.className() + "."
                        + "\n@param value The BigInteger valus")
                .body(bb -> {
                    IfBuilder<?> iff = bb.ifNull("value");
                    validationExceptions().createThrow(cb, iff, "BigInteger value may not be null"
                            + " - it is required.", null);
                    iff.endIf();
                    shape.getTrait(RangeTrait.class)
                            .ifPresent(range -> {
                                range.getMin().ifPresent(min -> {
                                    IfBuilder<?> test = bb.iff()
                                            .booleanExpression("value.compareTo(MIN_VALUE) < 0");
                                    validationExceptions().createThrow(cb, test, "Value must be greater than or "
                                            + "equal to " + min.toString() + ", but got ", "value");
                                    test.endIf();
                                });
                                range.getMax().ifPresent(max -> {
                                    IfBuilder<?> test = bb.iff()
                                            .booleanExpression("value.compareTo(MAX_VALUE) > 0");
                                    validationExceptions().createThrow(cb, test, "Value must be less than or "
                                            + "equal to " + max.toString() + ", but got ", "value");
                                    test.endIf();
                                });
                            });

                    bb.assign("this.value")
                            .toExpression("value");
                }));
    }

    public void generateRangeFieldsIfShapeHasRangeTrait(ClassBuilder<String> cb) {
        shape.getTrait(RangeTrait.class)
                .ifPresent(range -> {
                    range.getMin().ifPresent(min -> {
                        cb.field("MIN_VALUE")
                                .withModifier(PUBLIC, STATIC, FINAL)
                                .docComment("The minimum possible value of a " + cb.className() + ".")
                                .initializedWithNew(nb -> {
                                    nb.withStringLiteral(min.toString())
                                            .ofType("BigInteger");
                                }).ofType("BigInteger");
                    });
                    range.getMax().ifPresent(max -> {
                        cb.field("MAX_VALUE")
                                .withModifier(PUBLIC, STATIC, FINAL)
                                .docComment("The maximum possible value of a " + cb.className() + ".")
                                .initializedWithNew(nb -> {
                                    nb.withStringLiteral(max.toString())
                                            .ofType("BigInteger");
                                }).ofType("BigInteger");
                    });
                });
    }

    @Override
    protected <T, R, B extends ClassBuilder.BlockBuilderBase<R, B, ?>> void generateEqualsComparison(String other,
            ClassBuilder<?> cb, B bb) {
        bb.returningInvocationOf("equals")
                .withArgumentFromField("value")
                .of(other)
                .on("value");
    }

}
